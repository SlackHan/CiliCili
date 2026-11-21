package com.lanhan.cilicili.domain.parser

import com.lanhan.cilicili.domain.model.RawSiteResult
import com.lanhan.cilicili.domain.model.SiteRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Parses structured API search responses from Prowlarr (`/api/v1/search` JSON)
 * and Torznab/Newznab XML feeds (Prowlarr indexers, Jackett, Bitmagnet).
 */
object ApiSourceParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val DATE_OUT_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseProwlarrJson(
        rule: SiteRule,
        body: String,
        requestUrl: String,
    ): List<RawSiteResult> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = json.parseToJsonElement(trimmed)

        if (root is JsonObject) {
            val message = root["message"]?.primitiveOrNull()?.contentOrNull
                ?: root["error"]?.primitiveOrNull()?.contentOrNull
            if (!message.isNullOrBlank()) {
                throw IllegalStateException("Prowlarr 错误: $message")
            }
            return emptyList()
        }
        if (root !is JsonArray) return emptyList()

        val results = ArrayList<RawSiteResult>(root.size)
        for (element in root) {
            val obj = element as? JsonObject ?: continue
            val title = obj.str("title")
            if (title.isBlank()) continue

            val sizeBytes = obj.long("size") ?: -1L
            val sizeText = SizeParser.formatBytes(sizeBytes)
            val dateText = formatDateLabel(obj.str("publishDate"))
            val seeders = obj.int("seeders")
            val indexer = obj.str("indexer")
            val sourceName = if (indexer.isNotBlank()) "${rule.name} · $indexer" else rule.name

            val magnetField = obj.str("magnetUrl")
            val downloadUrl = obj.str("downloadUrl")
            val infoHashField = obj.str("infoHash")
            val guid = obj.str("guid")
            val infoUrl = obj.str("infoUrl")

            val normalized = listOf(magnetField, downloadUrl, infoHashField, guid)
                .firstNotNullOfOrNull { candidate ->
                    MagnetNormalizer.normalize(candidate, title)
                }

            val fallbackDetailUrl = listOf(downloadUrl, infoUrl, guid)
                .asSequence()
                .map { it.trim() }
                .filter { isNavigableUrl(it) }
                .map { RuleExtractor.resolveUrl(rule.mainUrl, requestUrl, it) }
                .firstOrNull { isHttpUrl(it) }
                .orEmpty()

            results.add(
                RawSiteResult(
                    siteId = rule.id,
                    siteName = sourceName,
                    title = title,
                    sizeText = sizeText,
                    sizeBytes = sizeBytes,
                    dateText = dateText,
                    detailUrl = fallbackDetailUrl,
                    magnetUri = normalized?.magnetUri,
                    infoHash = normalized?.infoHash,
                    seeders = seeders,
                ),
            )
        }
        return results
    }

    fun parseTorznabXml(
        rule: SiteRule,
        body: String,
        requestUrl: String,
    ): List<RawSiteResult> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val doc = Jsoup.parse(trimmed, requestUrl, Parser.xmlParser())

        doc.selectFirst("error")?.let { err ->
            val code = err.attr("code").trim()
            val desc = err.attr("description").trim().ifBlank { err.text().trim() }
            val msg = listOfNotNull(
                code.takeIf { it.isNotBlank() }?.let { "($it)" },
                desc.takeIf { it.isNotBlank() },
            ).joinToString(" ")
            throw IllegalStateException("Torznab 错误${if (msg.isNotBlank()) ": $msg" else ""}")
        }

        val items = doc.select("item")
        if (items.isEmpty()) return emptyList()

        val results = ArrayList<RawSiteResult>(items.size)
        for (item in items) {
            val title = item.selectFirst("title")?.text()?.trim().orEmpty()
            if (title.isBlank()) continue

            val torznabAttrs = parseTorznabAttrs(item)
            val enclosure = item.selectFirst("enclosure")
            val enclosureUrl = enclosure?.attr("url")?.trim().orEmpty()
            val link = item.selectFirst("link")?.text()?.trim().orEmpty()
            val guid = item.selectFirst("guid")?.text()?.trim().orEmpty()
            val comments = item.selectFirst("comments")?.text()?.trim().orEmpty()

            val sizeBytes = listOfNotNull(
                item.selectFirst("size")?.text()?.trim()?.toLongOrNull(),
                torznabAttrs["size"]?.toLongOrNull(),
                enclosure?.attr("length")?.trim()?.toLongOrNull(),
            ).firstOrNull { it > 0L } ?: -1L
            val sizeText = SizeParser.formatBytes(sizeBytes)

            val dateText = formatDateLabel(item.selectFirst("pubDate")?.text()?.trim().orEmpty())
            val seeders = torznabAttrs["seeders"]?.toIntOrNull()

            val indexerTag = item.selectFirst("prowlarrindexer, jackettindexer")?.text()?.trim().orEmpty()
            val sourceName = if (indexerTag.isNotBlank()) "${rule.name} · $indexerTag" else rule.name

            val normalized = listOfNotNull(
                torznabAttrs["magneturl"],
                torznabAttrs["infohash"],
                enclosureUrl,
                link,
                guid,
            ).firstNotNullOfOrNull { candidate ->
                MagnetNormalizer.normalize(candidate, title)
            }

            val fallbackDetailUrl = listOf(enclosureUrl, link, comments, guid)
                .asSequence()
                .map { it.trim() }
                .filter { isNavigableUrl(it) }
                .map { RuleExtractor.resolveUrl(rule.mainUrl, requestUrl, it) }
                .firstOrNull { isHttpUrl(it) }
                .orEmpty()

            results.add(
                RawSiteResult(
                    siteId = rule.id,
                    siteName = sourceName,
                    title = title,
                    sizeText = sizeText,
                    sizeBytes = sizeBytes,
                    dateText = dateText,
                    detailUrl = fallbackDetailUrl,
                    magnetUri = normalized?.magnetUri,
                    infoHash = normalized?.infoHash,
                    seeders = seeders,
                ),
            )
        }
        return results
    }

    private fun parseTorznabAttrs(item: Element): Map<String, String> {
        val attrs = HashMap<String, String>()
        for (el in item.children()) {
            val tag = el.tagName().lowercase()
            if (tag == "torznab:attr" || tag == "newznab:attr" || tag == "attr") {
                val name = el.attr("name").trim().lowercase()
                val value = el.attr("value").trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    attrs[name] = value
                }
            }
        }
        return attrs
    }

    internal fun formatDateLabel(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return ""
        val parsed = runCatching {
            Instant.parse(clean).atOffset(ZoneOffset.UTC).toLocalDate().format(DATE_OUT_FORMAT)
        }.recoverCatching {
            OffsetDateTime.parse(clean).toLocalDate().format(DATE_OUT_FORMAT)
        }.recoverCatching {
            ZonedDateTime.parse(clean, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toLocalDate()
                .format(DATE_OUT_FORMAT)
        }.getOrNull()
        return parsed ?: clean.take(16)
    }

    private fun isNavigableUrl(raw: String): Boolean =
        isHttpUrl(raw) || raw.startsWith("/")

    private fun isHttpUrl(raw: String): Boolean =
        raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)

    private fun kotlinx.serialization.json.JsonElement.primitiveOrNull(): JsonPrimitive? =
        this as? JsonPrimitive

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull
}

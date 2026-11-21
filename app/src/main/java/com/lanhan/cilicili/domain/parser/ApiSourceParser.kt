package com.lanhan.cilicili.domain.parser

import com.lanhan.cilicili.domain.model.RawSiteResult
import com.lanhan.cilicili.domain.model.SiteRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
 * Parses structured API search responses from Prowlarr (`/api/v1/search` JSON),
 * Torznab/Newznab XML feeds, generic JSON APIs, and standard RSS/Atom feeds.
 */
object ApiSourceParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val DATE_OUT_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
    private val EPOCH_10_REGEX = Regex("^\\d{10}$")
    private val EPOCH_13_REGEX = Regex("^\\d{13}$")
    private val ZERO_INFO_HASH = "0".repeat(40)

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

    fun parseGenericJson(
        rule: SiteRule,
        body: String,
        requestUrl: String,
    ): List<RawSiteResult> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = json.parseToJsonElement(trimmed)
        val items = resolveJsonArray(root, rule.groupExpr) ?: return emptyList()

        val results = ArrayList<RawSiteResult>(items.size)
        for (element in items) {
            val obj = element as? JsonObject ?: continue
            val title = resolveJsonField(obj, rule.titleExpr, "title|name")
            if (title.isBlank() || title.equals("No results returned", ignoreCase = true)) continue

            val magnetCandidate = resolveJsonField(
                obj,
                rule.magnetExpr,
                "magnet|magnet_uri|magnetUrl|info_hash|infoHash|hash",
            )
            val normalized = MagnetNormalizer.normalize(magnetCandidate, title)
            if (normalized?.infoHash == ZERO_INFO_HASH) continue

            val rawSize = resolveJsonField(obj, rule.sizeExpr, "size|total_size|size_bytes")
            val numericSize = rawSize.toLongOrNull()
            val (sizeBytes, sizeText) = if (numericSize != null && numericSize > 0L) {
                numericSize to SizeParser.formatBytes(numericSize)
            } else {
                SizeParser.parseBytes(rawSize) to SizeParser.cleanSizeLabel(rawSize)
            }

            val rawDate = resolveJsonField(
                obj,
                rule.dateExpr,
                "date|added|timestamp|publishDate|created_at",
            )
            val dateText = formatDateLabel(rawDate)

            val rawUrl = resolveJsonField(obj, rule.urlExpr, "url|link|infoUrl|detail_url")
            val fallbackDetailUrl = rawUrl
                .takeIf { isNavigableUrl(it) }
                ?.let { RuleExtractor.resolveUrl(rule.mainUrl, requestUrl, it) }
                ?.takeIf { isHttpUrl(it) }
                .orEmpty()

            val seeders = resolveJsonField(obj, "", "seeders|seeds").toIntOrNull()

            results.add(
                RawSiteResult(
                    siteId = rule.id,
                    siteName = rule.name,
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

    fun parseRssXml(
        rule: SiteRule,
        body: String,
        requestUrl: String,
    ): List<RawSiteResult> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val doc = Jsoup.parse(trimmed, requestUrl, Parser.xmlParser())

        val items = doc.select("item").ifEmpty { doc.select("entry") }
        if (items.isEmpty()) return emptyList()

        val results = ArrayList<RawSiteResult>(items.size)
        for (item in items) {
            val title = item.selectFirst("title")?.text()?.trim().orEmpty()
            if (title.isBlank()) continue

            val torznabAttrs = parseTorznabAttrs(item)
            var nyaaSize: String? = null
            var nyaaSeeders: String? = null
            var nyaaInfoHash: String? = null
            var torrentInfoHash: String? = null
            var torrentMagnetUri: String? = null
            var torrentContentLength: String? = null

            for (el in item.children()) {
                val tag = el.tagName().lowercase()
                when (tag) {
                    "nyaa:size" -> nyaaSize = el.text().trim()
                    "nyaa:seeders" -> nyaaSeeders = el.text().trim()
                    "nyaa:infohash" -> nyaaInfoHash = el.text().trim()
                    "torrent" -> {
                        for (child in el.children()) {
                            when (child.tagName().lowercase()) {
                                "infohash" -> torrentInfoHash = child.text().trim()
                                "magneturi" -> torrentMagnetUri = child.text().trim()
                                "contentlength" -> torrentContentLength = child.text().trim()
                            }
                        }
                    }
                }
            }

            val enclosure = item.selectFirst("enclosure")
            val enclosureUrl = enclosure?.attr("url")?.trim().orEmpty()
            val enclosureLen = enclosure?.attr("length")?.trim().orEmpty()

            val linkUrls = item.select("link").mapNotNull { el ->
                el.text().trim().ifBlank { el.attr("href").trim() }.takeIf { it.isNotEmpty() }
            }
            val guid = item.selectFirst("guid, id")?.text()?.trim().orEmpty()
            val comments = item.selectFirst("comments")?.text()?.trim().orEmpty()
            val description = item.selectFirst("description, summary, content")?.text()?.trim().orEmpty()

            val sizeCandidates = listOfNotNull(
                item.selectFirst("size")?.text()?.trim(),
                torznabAttrs["size"],
                nyaaSize,
                torrentContentLength,
                enclosureLen,
            ).filter { it.isNotBlank() }

            var sizeBytes = -1L
            var sizeText = ""
            for (raw in sizeCandidates) {
                val numeric = raw.toLongOrNull()
                if (numeric != null) {
                    if (numeric > 0L) {
                        sizeBytes = numeric
                        sizeText = SizeParser.formatBytes(numeric)
                        break
                    }
                    continue
                }
                val parsed = SizeParser.parseBytes(raw)
                if (parsed > 0L) {
                    sizeBytes = parsed
                    sizeText = SizeParser.cleanSizeLabel(raw)
                    break
                }
            }

            val dateText = formatDateLabel(
                item.selectFirst("pubDate, published, updated")?.text()?.trim().orEmpty(),
            )
            val seeders = torznabAttrs["seeders"]?.toIntOrNull() ?: nyaaSeeders?.toIntOrNull()

            val magnetCandidates = listOfNotNull(
                torznabAttrs["magneturl"],
                torznabAttrs["infohash"],
                nyaaInfoHash,
                torrentMagnetUri,
                torrentInfoHash,
                enclosureUrl,
            ) + linkUrls + listOf(guid, description)

            val normalized = magnetCandidates.firstNotNullOfOrNull { candidate ->
                MagnetNormalizer.normalize(candidate, title)
            }

            val fallbackDetailUrl = (linkUrls + listOf(comments, guid, enclosureUrl))
                .asSequence()
                .map { it.trim() }
                .filter { isNavigableUrl(it) }
                .map { RuleExtractor.resolveUrl(rule.mainUrl, requestUrl, it) }
                .firstOrNull { isHttpUrl(it) }
                .orEmpty()

            results.add(
                RawSiteResult(
                    siteId = rule.id,
                    siteName = rule.name,
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

    private fun resolveJsonArray(root: JsonElement, groupExpr: String): JsonArray? {
        val clean = groupExpr.trim()
        if (clean.isEmpty() || clean == "$") return root as? JsonArray
        val path = clean.removePrefix("$.").removePrefix("$")
        if (path.isEmpty()) return root as? JsonArray
        var current: JsonElement = root
        for (segment in path.split('.')) {
            val key = segment.trim()
            if (key.isEmpty()) continue
            current = when (current) {
                is JsonObject -> current[key] ?: return null
                is JsonArray -> {
                    val idx = key.toIntOrNull() ?: return null
                    current.getOrNull(idx) ?: return null
                }
                else -> return null
            }
        }
        return current as? JsonArray
    }

    private fun resolveJsonField(
        obj: JsonObject,
        expr: String,
        defaultFallback: String,
    ): String {
        val effective = expr.trim().ifBlank { defaultFallback }
        for (alt in effective.split('|')) {
            val path = alt.trim()
            if (path.isEmpty()) continue
            val value = extractJsonPath(obj, path)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun extractJsonPath(obj: JsonObject, path: String): String {
        var current: JsonElement = obj
        for (segment in path.split('.')) {
            val key = segment.trim()
            if (key.isEmpty()) continue
            current = when (current) {
                is JsonObject -> current[key] ?: return ""
                is JsonArray -> {
                    val idx = key.toIntOrNull() ?: return ""
                    current.getOrNull(idx) ?: return ""
                }
                else -> return ""
            }
        }
        val prim = current as? JsonPrimitive ?: return ""
        if (prim.isString && prim.content.equals("null", ignoreCase = true)) return ""
        return prim.contentOrNull?.trim().orEmpty()
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
        if (EPOCH_10_REGEX.matches(clean)) {
            return runCatching {
                Instant.ofEpochSecond(clean.toLong()).atOffset(ZoneOffset.UTC)
                    .toLocalDate()
                    .format(DATE_OUT_FORMAT)
            }.getOrDefault(clean.take(16))
        }
        if (EPOCH_13_REGEX.matches(clean)) {
            return runCatching {
                Instant.ofEpochMilli(clean.toLong()).atOffset(ZoneOffset.UTC)
                    .toLocalDate()
                    .format(DATE_OUT_FORMAT)
            }.getOrDefault(clean.take(16))
        }
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

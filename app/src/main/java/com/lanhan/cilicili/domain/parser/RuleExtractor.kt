package com.lanhan.cilicili.domain.parser

import com.lanhan.cilicili.domain.model.RawSiteResult
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSourceType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.seimicrawler.xpath.JXDocument
import org.seimicrawler.xpath.JXNode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * XPath-first HTML extractor compatible with `site-1.0.0.json` rules, with resilient
 * single-field fallback and automatic `/hash/<hex>` normalization.
 */
object RuleExtractor {

    private val DATE_CLEAN_REGEX = Regex("\\s+")
    private const val API_PAGE_SIZE = 50

    /**
     * Expands `[keyword]`, `[page]`, and `[offset]` placeholders in [SiteRule.searchUrl].
     */
    fun buildSearchUrl(rule: SiteRule, keyword: String, page: Int): String {
        val encodedKeyword = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val offset = (page - 1).coerceAtLeast(0) * API_PAGE_SIZE
        return rule.searchUrl
            .replace("[keyword]", encodedKeyword)
            .replace("[page]", page.toString())
            .replace("[offset]", offset.toString())
    }

    /**
     * Dispatches parsing of a search response body according to [SiteRule.sourceType].
     */
    fun parseSearchResponse(
        rule: SiteRule,
        body: String,
        requestUrl: String,
    ): List<RawSiteResult> = when (rule.sourceType) {
        SiteSourceType.PROWLARR -> ApiSourceParser.parseProwlarrJson(rule, body, requestUrl)
        SiteSourceType.TORZNAB -> ApiSourceParser.parseTorznabXml(rule, body, requestUrl)
        SiteSourceType.HTML -> parseSearchPage(rule, body, requestUrl)
    }

    /**
     * Parses search result page [html] into [RawSiteResult] items using [rule].
     */
    fun parseSearchPage(
        rule: SiteRule,
        html: String,
        requestUrl: String,
    ): List<RawSiteResult> {
        if (html.isBlank() || rule.groupExpr.isBlank()) return emptyList()
        val baseUrl = rule.mainUrl.ifBlank { requestUrl }
        val doc = Jsoup.parse(html, baseUrl)
        val jxDoc = JXDocument.create(doc)
        val groupNodes = runCatching { jxDoc.selN(rule.groupExpr) }.getOrNull().orEmpty()
        if (groupNodes.isEmpty()) return emptyList()

        val results = ArrayList<RawSiteResult>(groupNodes.size)
        for (node in groupNodes) {
            val rawTitle = evalText(node, rule.titleExpr)
            if (rawTitle.isBlank()) continue

            val rawDate = evalText(node, rule.dateExpr)
                .replace(DATE_CLEAN_REGEX, " ")
                .trim()
            val rawSize = evalText(node, rule.sizeExpr)
            val sizeBytes = SizeParser.parseBytes(rawSize)
            val sizeText = SizeParser.cleanSizeLabel(rawSize)

            val rawUrl = evalValue(node, rule.urlExpr)
            val resolvedDetailUrl = resolveUrl(baseUrl, requestUrl, rawUrl)

            // When urlExpr is blank, magnetExpr is evaluated against the list-item node directly.
            var resolvedMagnet: String? = null
            var infoHash: String? = null
            var fallbackDetailUrl = resolvedDetailUrl

            if (rule.urlExpr.isBlank() && rule.magnetExpr.isNotBlank()) {
                val rawMagnetCandidate = evalValue(node, rule.magnetExpr)
                val normalized = MagnetNormalizer.normalize(rawMagnetCandidate, rawTitle)
                if (normalized != null) {
                    resolvedMagnet = normalized.magnetUri
                    infoHash = normalized.infoHash
                } else if (rawMagnetCandidate.isNotBlank() && fallbackDetailUrl.isBlank()) {
                    // Some rules put a non-hash `/detail/123` link in `magnet` with empty `url`:
                    // fall back to treating it as a detail page URL.
                    fallbackDetailUrl = resolveUrl(baseUrl, requestUrl, rawMagnetCandidate)
                }
            }

            results.add(
                RawSiteResult(
                    siteId = rule.id,
                    siteName = rule.name,
                    title = rawTitle,
                    sizeText = sizeText,
                    sizeBytes = sizeBytes,
                    dateText = rawDate,
                    detailUrl = fallbackDetailUrl,
                    magnetUri = resolvedMagnet,
                    infoHash = infoHash,
                ),
            )
        }
        return results
    }

    /**
     * Parses a detail page [html] for a magnet URI using [rule.magnetExpr] with a fallback
     * DOM/regex scan when the configured XPath misses.
     */
    fun parseDetailMagnet(
        rule: SiteRule,
        html: String,
        detailUrl: String,
        title: String? = null,
    ): String? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html, detailUrl)

        if (rule.magnetExpr.isNotBlank()) {
            val jxDoc = JXDocument.create(doc)
            val nodes = runCatching { jxDoc.selN(rule.magnetExpr) }.getOrNull().orEmpty()
            for (node in nodes) {
                val candidate = extractFromNode(node)
                MagnetNormalizer.normalize(candidate, title)?.let { return it.magnetUri }
            }
        }

        // Fallback: look for any `<a href="magnet:...">` or raw magnet string in HTML.
        doc.selectFirst("a[href^=magnet:]")?.attr("href")?.let { href ->
            MagnetNormalizer.normalize(href, title)?.let { return it.magnetUri }
        }
        return MagnetNormalizer.normalize(html, title)
            ?.takeIf { it.magnetUri.startsWith("magnet:?") && html.contains("urn:btih:", ignoreCase = true) }
            ?.magnetUri
    }

    private fun evalText(parent: JXNode, expr: String): String {
        if (expr.isBlank()) return ""
        return runCatching {
            val nodes = parent.sel(expr)
            nodes.firstOrNull()?.let { node ->
                if (node.isElement) node.asElement().text().trim() else node.asString().trim()
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun evalValue(parent: JXNode, expr: String): String {
        if (expr.isBlank()) return ""
        return runCatching {
            val nodes = parent.sel(expr)
            for (node in nodes) {
                val extracted = extractFromNode(node)
                if (extracted.isNotBlank()) return@runCatching extracted
            }
            ""
        }.getOrDefault("")
    }

    private fun extractFromNode(node: JXNode): String {
        if (!node.isElement) return node.asString().trim()
        val element = node.asElement()
        val href = element.attr("href").trim()
        val value = element.attr("value").trim()
        val text = element.text().trim()
        return listOf(href, value, text).firstOrNull {
            it.contains("magnet:?", ignoreCase = true) ||
                MagnetNormalizer.extractHexInfoHash(it) != null
        } ?: listOf(href, value, text).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    internal fun resolveUrl(mainUrl: String, requestUrl: String, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("magnet:", ignoreCase = true)
        ) {
            return trimmed
        }
        val base = (requestUrl.toHttpUrlOrNull() ?: mainUrl.toHttpUrlOrNull()) ?: return trimmed
        return base.resolve(trimmed)?.toString() ?: trimmed
    }
}

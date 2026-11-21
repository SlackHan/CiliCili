package com.lanhan.cilicili.domain.parser

import com.lanhan.cilicili.domain.model.RawSiteResult
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSourceType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.seimicrawler.xpath.JXDocument
import org.seimicrawler.xpath.JXNode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * XPath- and CSS-capable HTML extractor compatible with `site-1.0.0.json` rules
 * as well as JSON API and RSS/Atom sources, with resilient single-field fallback
 * and automatic `/hash/<hex>` normalization.
 */
object RuleExtractor {

    private val DATE_CLEAN_REGEX = Regex("\\s+")
    private val CSS_ATTR_SUFFIX = Regex("^[a-zA-Z0-9_-]+$")
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
        SiteSourceType.RSS -> ApiSourceParser.parseRssXml(rule, body, requestUrl)
        SiteSourceType.JSON_API -> ApiSourceParser.parseGenericJson(rule, body, requestUrl)
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

        if (isCssExpr(rule.groupExpr)) {
            val (groupSel, _) = parseCssSpec(rule.groupExpr)
            if (groupSel.isEmpty()) return emptyList()
            val groupElements = runCatching { doc.select(groupSel) }.getOrNull().orEmpty()
            if (groupElements.isEmpty()) return emptyList()

            val results = ArrayList<RawSiteResult>(groupElements.size)
            for (el in groupElements) {
                val rawTitle = evalElementText(el, rule.titleExpr)
                if (rawTitle.isBlank()) continue
                val rawDate = evalElementText(el, rule.dateExpr)
                val rawSize = evalElementText(el, rule.sizeExpr)
                val rawUrl = evalElementValue(el, rule.urlExpr)
                val rawMagnetCandidate = if (rule.urlExpr.isBlank() && rule.magnetExpr.isNotBlank()) {
                    evalElementValue(el, rule.magnetExpr)
                } else {
                    ""
                }
                results.add(
                    buildRawResult(
                        rule = rule,
                        baseUrl = baseUrl,
                        requestUrl = requestUrl,
                        rawTitle = rawTitle,
                        rawDate = rawDate,
                        rawSize = rawSize,
                        rawUrl = rawUrl,
                        rawMagnetCandidate = rawMagnetCandidate,
                    ),
                )
            }
            return results
        }

        val jxDoc = JXDocument.create(doc)
        val groupNodes = runCatching { jxDoc.selN(rule.groupExpr) }.getOrNull().orEmpty()
        if (groupNodes.isEmpty()) return emptyList()

        val results = ArrayList<RawSiteResult>(groupNodes.size)
        for (node in groupNodes) {
            val rawTitle = evalText(node, rule.titleExpr)
            if (rawTitle.isBlank()) continue
            val rawDate = evalText(node, rule.dateExpr)
            val rawSize = evalText(node, rule.sizeExpr)
            val rawUrl = evalValue(node, rule.urlExpr)
            val rawMagnetCandidate = if (rule.urlExpr.isBlank() && rule.magnetExpr.isNotBlank()) {
                evalValue(node, rule.magnetExpr)
            } else {
                ""
            }
            results.add(
                buildRawResult(
                    rule = rule,
                    baseUrl = baseUrl,
                    requestUrl = requestUrl,
                    rawTitle = rawTitle,
                    rawDate = rawDate,
                    rawSize = rawSize,
                    rawUrl = rawUrl,
                    rawMagnetCandidate = rawMagnetCandidate,
                ),
            )
        }
        return results
    }

    private fun buildRawResult(
        rule: SiteRule,
        baseUrl: String,
        requestUrl: String,
        rawTitle: String,
        rawDate: String,
        rawSize: String,
        rawUrl: String,
        rawMagnetCandidate: String,
    ): RawSiteResult {
        val cleanDate = rawDate.replace(DATE_CLEAN_REGEX, " ").trim()
        val sizeBytes = SizeParser.parseBytes(rawSize)
        val sizeText = SizeParser.cleanSizeLabel(rawSize)
        val resolvedDetailUrl = resolveUrl(baseUrl, requestUrl, rawUrl)

        var resolvedMagnet: String? = null
        var infoHash: String? = null
        var fallbackDetailUrl = resolvedDetailUrl

        if (rule.urlExpr.isBlank() && rule.magnetExpr.isNotBlank()) {
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

        return RawSiteResult(
            siteId = rule.id,
            siteName = rule.name,
            title = rawTitle,
            sizeText = sizeText,
            sizeBytes = sizeBytes,
            dateText = cleanDate,
            detailUrl = fallbackDetailUrl,
            magnetUri = resolvedMagnet,
            infoHash = infoHash,
        )
    }

    /**
     * Parses a detail page [html] for a magnet URI using [rule.magnetExpr] with a fallback
     * DOM/regex scan when the configured XPath/CSS selector misses.
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
            if (isCssExpr(rule.magnetExpr)) {
                val (selector, attr) = parseCssSpec(rule.magnetExpr)
                if (selector.isNotEmpty()) {
                    val elements = runCatching { doc.select(selector) }.getOrNull().orEmpty()
                    for (el in elements) {
                        val candidate = if (attr != null) el.attr(attr).trim() else extractFromElement(el)
                        MagnetNormalizer.normalize(candidate, title)?.let { return it.magnetUri }
                    }
                }
            } else {
                val jxDoc = JXDocument.create(doc)
                val nodes = runCatching { jxDoc.selN(rule.magnetExpr) }.getOrNull().orEmpty()
                for (node in nodes) {
                    val candidate = extractFromNode(node)
                    MagnetNormalizer.normalize(candidate, title)?.let { return it.magnetUri }
                }
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

    private fun isCssExpr(expr: String): Boolean =
        expr.trimStart().startsWith("css:", ignoreCase = true)

    private fun parseCssSpec(expr: String): Pair<String, String?> {
        val body = expr.trimStart().substring(4).trim()
        if (body.isEmpty()) return "" to null
        if (body.startsWith("@")) {
            val attr = body.substring(1).trim()
            if (CSS_ATTR_SUFFIX.matches(attr)) return "" to attr
        }
        val atIdx = body.lastIndexOf('@')
        if (atIdx > 0) {
            val prefix = body.substring(0, atIdx).trim()
            val suffix = body.substring(atIdx + 1).trim()
            val hasUnclosedBracket = prefix.lastIndexOf('[') > prefix.lastIndexOf(']')
            if (prefix.isNotEmpty() && !hasUnclosedBracket && CSS_ATTR_SUFFIX.matches(suffix)) {
                return prefix to suffix
            }
        }
        return body to null
    }

    private fun evalCssText(parent: Element, expr: String): String {
        val (selector, attr) = parseCssSpec(expr)
        val target = if (selector.isEmpty()) {
            parent
        } else {
            runCatching { parent.selectFirst(selector) }.getOrNull() ?: return ""
        }
        return if (attr != null) target.attr(attr).trim() else target.text().trim()
    }

    private fun evalCssValue(parent: Element, expr: String): String {
        val (selector, attr) = parseCssSpec(expr)
        val targets = if (selector.isEmpty()) {
            listOf(parent)
        } else {
            runCatching { parent.select(selector) }.getOrNull().orEmpty()
        }
        for (el in targets) {
            val extracted = if (attr != null) el.attr(attr).trim() else extractFromElement(el)
            if (extracted.isNotBlank()) return extracted
        }
        return ""
    }

    private fun evalElementText(parent: Element, expr: String): String {
        if (expr.isBlank()) return ""
        if (isCssExpr(expr)) return evalCssText(parent, expr)
        return runCatching {
            val subDoc = JXDocument.create(parent.outerHtml())
            val nodes = subDoc.selN(expr)
            nodes.firstOrNull()?.let { node ->
                if (node.isElement) node.asElement().text().trim() else node.asString().trim()
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun evalElementValue(parent: Element, expr: String): String {
        if (expr.isBlank()) return ""
        if (isCssExpr(expr)) return evalCssValue(parent, expr)
        return runCatching {
            val subDoc = JXDocument.create(parent.outerHtml())
            val nodes = subDoc.selN(expr)
            for (node in nodes) {
                val extracted = extractFromNode(node)
                if (extracted.isNotBlank()) return@runCatching extracted
            }
            ""
        }.getOrDefault("")
    }

    private fun evalText(parent: JXNode, expr: String): String {
        if (expr.isBlank()) return ""
        if (isCssExpr(expr) && parent.isElement) {
            return evalCssText(parent.asElement(), expr)
        }
        return runCatching {
            val nodes = parent.sel(expr)
            nodes.firstOrNull()?.let { node ->
                if (node.isElement) node.asElement().text().trim() else node.asString().trim()
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun evalValue(parent: JXNode, expr: String): String {
        if (expr.isBlank()) return ""
        if (isCssExpr(expr) && parent.isElement) {
            return evalCssValue(parent.asElement(), expr)
        }
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
        return extractFromElement(node.asElement())
    }

    private fun extractFromElement(element: Element): String {
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

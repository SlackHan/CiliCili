package com.lanhan.cilicili.domain.model

/**
 * Single parsed search result produced by one site before cross-site InfoHash deduplication.
 */
data class RawSiteResult(
    val siteId: Long,
    val siteName: String,
    val title: String,
    val sizeText: String,
    val sizeBytes: Long,
    val dateText: String,
    val detailUrl: String,
    val magnetUri: String?,
    val infoHash: String?,
    val seeders: Int? = null,
) {
    /**
     * Stable key for cross-site deduplication: lowercase 40-char hex infoHash when known,
     * otherwise scoped by siteId + detailUrl/title so unresolved detail items don't collide prematurely.
     */
    val dedupKey: String
        get() = infoHash?.takeIf { it.isNotBlank() }
            ?: "unresolved:${siteId}:${detailUrl.ifBlank { title }}"
}

/**
 * Reference back to one contributing site for an aggregated torrent result.
 */
data class SiteSourceRef(
    val siteId: Long,
    val siteName: String,
    val detailUrl: String,
    val magnetUri: String?,
)

/**
 * Deduplicated magnet item displayed in the search result list.
 */
data class AggregatedMagnetItem(
    val dedupKey: String,
    val infoHash: String?,
    val title: String,
    val sizeText: String,
    val sizeBytes: Long,
    val dateText: String,
    val magnetUri: String?,
    val sources: List<SiteSourceRef>,
    val seeders: Int? = null,
) {
    val primarySource: SiteSourceRef get() = sources.first()
}

/**
 * Live per-site execution state during a search or batch health check.
 */
sealed interface SiteSearchStatus {
    data object Pending : SiteSearchStatus
    data object Running : SiteSearchStatus
    data class Success(val count: Int, val latencyMs: Long) : SiteSearchStatus
    data class ChallengeRequired(val challengeUrl: String, val latencyMs: Long) : SiteSearchStatus
    data class Failed(val reason: String, val latencyMs: Long) : SiteSearchStatus
}

data class SiteExecutionState(
    val site: SiteRule,
    val status: SiteSearchStatus,
)

enum class SortOption(val label: String) {
    RELEVANCE("综合排序"),
    SEEDERS_DESC("做种数优先"),
    SIZE_DESC("体积从大到小"),
    SIZE_ASC("体积从小到大"),
    DATE_DESC("收录时间优先"),
    SOURCES_DESC("多站命中优先"),
}

enum class MinSizeFilter(val label: String, val minBytes: Long) {
    ALL("全部大小", -1L),
    OVER_20_MB("> 20 MB", 20L * 1024 * 1024),
    OVER_100_MB("> 100 MB", 100L * 1024 * 1024),
    OVER_500_MB("> 500 MB", 500L * 1024 * 1024),
    OVER_1_GB("> 1 GB", 1024L * 1024 * 1024),
}

enum class DohProvider(val label: String, val url: String, val bootstrapIps: List<String>) {
    SYSTEM("系统默认 DNS", "", emptyList()),
    ALIDNS("阿里 AliDNS", "https://dns.alidns.com/dns-query", listOf("223.5.5.5", "223.6.6.6")),
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
}

enum class DefaultResultAction(val label: String) {
    ASK("弹出操作菜单"),
    COPY("直接复制磁力链"),
    OPEN_DOWNLOADER("直接唤起下载器"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式"),
}

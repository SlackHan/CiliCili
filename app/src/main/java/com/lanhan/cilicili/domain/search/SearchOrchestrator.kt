package com.lanhan.cilicili.domain.search

import com.lanhan.cilicili.data.network.FetchResult
import com.lanhan.cilicili.data.network.SiteHttpFetcher
import com.lanhan.cilicili.domain.model.AggregatedMagnetItem
import com.lanhan.cilicili.domain.model.RawSiteResult
import com.lanhan.cilicili.domain.model.SiteExecutionState
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSearchStatus
import com.lanhan.cilicili.domain.model.SiteSourceRef
import com.lanhan.cilicili.domain.parser.MagnetNormalizer
import com.lanhan.cilicili.domain.parser.RuleExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

sealed interface SearchStreamEvent {
    data class SiteUpdated(val state: SiteExecutionState) : SearchStreamEvent
    data class ResultsBatch(val siteId: Long, val items: List<RawSiteResult>) : SearchStreamEvent
}

sealed interface DetailResolveResult {
    data class Success(val magnetUri: String, val infoHash: String?) : DetailResolveResult
    data class ChallengeRequired(val challengeUrl: String) : DetailResolveResult
    data class Failed(val reason: String) : DetailResolveResult
}

/**
 * Coordinates concurrent multi-site search, progressive streaming results,
 * InfoHash deduplication, and on-demand detail-page magnet resolution.
 */
class SearchOrchestrator(
    private val fetcher: SiteHttpFetcher,
) {
    private val detailMagnetCache = ConcurrentHashMap<String, String>()

    fun searchStreaming(
        keyword: String,
        page: Int,
        sites: List<SiteRule>,
        maxConcurrency: Int,
    ): Flow<SearchStreamEvent> = channelFlow {
        if (keyword.isBlank() || sites.isEmpty()) return@channelFlow
        val semaphore = Semaphore(maxConcurrency.coerceIn(1, 16))

        sites.forEach { site ->
            launch {
                send(SearchStreamEvent.SiteUpdated(SiteExecutionState(site, SiteSearchStatus.Running)))
                val (status, items) = semaphore.withPermit {
                    executeSingleSite(site, keyword, page)
                }
                if (items.isNotEmpty()) {
                    send(SearchStreamEvent.ResultsBatch(site.id, items))
                }
                send(SearchStreamEvent.SiteUpdated(SiteExecutionState(site, status)))
            }
        }
    }

    suspend fun searchSingleSite(
        site: SiteRule,
        keyword: String,
        page: Int = 1,
    ): Pair<SiteSearchStatus, List<RawSiteResult>> = executeSingleSite(site, keyword, page)

    /**
     * Resolves a magnet URI for a two-step result item (trying each available source's detail page).
     */
    suspend fun resolveMagnet(
        item: AggregatedMagnetItem,
        rulesById: Map<Long, SiteRule>,
    ): DetailResolveResult {
        item.magnetUri?.takeIf { it.isNotBlank() }?.let { cached ->
            return DetailResolveResult.Success(cached, item.infoHash)
        }
        val cacheKey = item.dedupKey
        detailMagnetCache[cacheKey]?.let { cached ->
            return DetailResolveResult.Success(
                magnetUri = cached,
                infoHash = MagnetNormalizer.extractHexInfoHash(cached),
            )
        }

        var challengeUrl: String? = null
        var lastError = "未找到磁力链接"

        for (source in item.sources) {
            source.magnetUri?.takeIf { it.isNotBlank() }?.let { direct ->
                detailMagnetCache[cacheKey] = direct
                return DetailResolveResult.Success(direct, MagnetNormalizer.extractHexInfoHash(direct))
            }
            val rule = rulesById[source.siteId] ?: continue
            val detailUrl = source.detailUrl
            if (detailUrl.isBlank()) continue

            when (val res = fetcher.fetch(rule, detailUrl)) {
                is FetchResult.Success -> {
                    val magnet = RuleExtractor.parseDetailMagnet(rule, res.html, res.finalUrl, item.title)
                    if (!magnet.isNullOrBlank()) {
                        detailMagnetCache[cacheKey] = magnet
                        return DetailResolveResult.Success(
                            magnetUri = magnet,
                            infoHash = MagnetNormalizer.extractHexInfoHash(magnet),
                        )
                    } else {
                        lastError = "详情页未解析到磁力链接"
                    }
                }
                is FetchResult.RedirectToMagnet -> {
                    detailMagnetCache[cacheKey] = res.magnetUri
                    return DetailResolveResult.Success(
                        magnetUri = res.magnetUri,
                        infoHash = MagnetNormalizer.extractHexInfoHash(res.magnetUri),
                    )
                }
                is FetchResult.ChallengeRequired -> {
                    if (challengeUrl == null) challengeUrl = res.url
                    lastError = "站点需要过盾验证 (${res.statusCode})"
                }
                is FetchResult.HttpFailure -> lastError = res.message
                is FetchResult.NetworkFailure -> lastError = res.message
            }
        }

        if (challengeUrl != null) {
            return DetailResolveResult.ChallengeRequired(challengeUrl)
        }
        return DetailResolveResult.Failed(lastError)
    }

    suspend fun fetchRemoteText(url: String): Result<String> {
        val dummyRule = SiteRule(
            id = -1L,
            name = "Subscription",
            info = "",
            enabled = true,
            sortOrder = 0,
            time = "",
            updateTime = "",
            method = "GET",
            mainUrl = url,
            searchUrl = url,
            headers = emptyMap(),
            groupExpr = "",
            titleExpr = "",
            dateExpr = "",
            sizeExpr = "",
            urlExpr = "",
            magnetExpr = "",
        )
        return when (val res = fetcher.fetch(dummyRule, url)) {
            is FetchResult.Success -> Result.success(res.html)
            is FetchResult.RedirectToMagnet -> Result.failure(IllegalStateException("订阅链接返回非预期磁力重定向"))
            is FetchResult.ChallengeRequired -> Result.failure(IllegalStateException("订阅链接触发反爬验证 (${res.statusCode})"))
            is FetchResult.HttpFailure -> Result.failure(IllegalStateException(res.message))
            is FetchResult.NetworkFailure -> Result.failure(IllegalStateException(res.message))
        }
    }

    private suspend fun executeSingleSite(
        site: SiteRule,
        keyword: String,
        page: Int,
    ): Pair<SiteSearchStatus, List<RawSiteResult>> {
        if (page > 1 &&
            !site.searchUrl.contains("[page]") &&
            !site.searchUrl.contains("[offset]")
        ) {
            return SiteSearchStatus.Success(0, 0L) to emptyList()
        }
        val url = RuleExtractor.buildSearchUrl(site, keyword, page)
        return when (val fetchResult = fetcher.fetch(site, url)) {
            is FetchResult.Success -> {
                val parsed = runCatching {
                    RuleExtractor.parseSearchResponse(site, fetchResult.html, fetchResult.finalUrl)
                }
                parsed.fold(
                    onSuccess = { items ->
                        SiteSearchStatus.Success(items.size, fetchResult.latencyMs) to items
                    },
                    onFailure = { err ->
                        SiteSearchStatus.Failed(
                            reason = err.message?.takeIf { it.isNotBlank() } ?: "规则解析失败",
                            latencyMs = fetchResult.latencyMs,
                        ) to emptyList()
                    },
                )
            }
            is FetchResult.RedirectToMagnet -> {
                SiteSearchStatus.Failed("搜索页重定向到磁力链", fetchResult.latencyMs) to emptyList()
            }
            is FetchResult.ChallengeRequired -> {
                SiteSearchStatus.ChallengeRequired(fetchResult.url, fetchResult.latencyMs) to emptyList()
            }
            is FetchResult.HttpFailure -> {
                SiteSearchStatus.Failed(fetchResult.message, fetchResult.latencyMs) to emptyList()
            }
            is FetchResult.NetworkFailure -> {
                SiteSearchStatus.Failed(fetchResult.message, fetchResult.latencyMs) to emptyList()
            }
        }
    }

    companion object {
        /**
         * Aggregates raw results from multiple sites, merging entries that share an InfoHash.
         */
        fun aggregateAndDeduplicate(rawItems: List<RawSiteResult>): List<AggregatedMagnetItem> {
            val grouped = LinkedHashMap<String, MutableList<RawSiteResult>>()
            for (item in rawItems) {
                grouped.getOrPut(item.dedupKey) { mutableListOf() }.add(item)
            }
            return grouped.map { (key, group) ->
                val bestTitle = group.maxByOrNull { it.title.length }?.title ?: group.first().title
                val bestSizeItem = group.firstOrNull { it.sizeBytes > 0 } ?: group.first()
                val bestDate = group.firstOrNull { it.dateText.isNotBlank() }?.dateText.orEmpty()
                val bestMagnet = group.firstNotNullOfOrNull { it.magnetUri }
                val bestHash = group.firstNotNullOfOrNull { it.infoHash }
                val bestSeeders = group.mapNotNull { it.seeders }.maxOrNull()
                val sources = group
                    .distinctBy { Triple(it.siteId, it.siteName, it.detailUrl) }
                    .map {
                        SiteSourceRef(
                            siteId = it.siteId,
                            siteName = it.siteName,
                            detailUrl = it.detailUrl,
                            magnetUri = it.magnetUri,
                        )
                    }
                AggregatedMagnetItem(
                    dedupKey = key,
                    infoHash = bestHash,
                    title = bestTitle,
                    sizeText = bestSizeItem.sizeText,
                    sizeBytes = bestSizeItem.sizeBytes,
                    dateText = bestDate,
                    magnetUri = bestMagnet,
                    sources = sources,
                    seeders = bestSeeders,
                )
            }
        }
    }
}

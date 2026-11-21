package com.lanhan.cilicili.domain.search

import com.lanhan.cilicili.data.local.SiteRuleRepository
import com.lanhan.cilicili.data.network.FetchResult
import com.lanhan.cilicili.data.network.SiteHttpFetcher
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.parser.RuleExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class HealthCheckProgress(
    val completed: Int,
    val total: Int,
    val lastCheckedSite: String,
)

/**
 * Concurrently probes configured sites with a test keyword and persists health status & latency.
 */
class SiteHealthChecker(
    private val fetcher: SiteHttpFetcher,
    private val ruleRepository: SiteRuleRepository,
) {
    fun checkAll(
        sites: List<SiteRule>,
        probeKeyword: String = "ubuntu",
        maxConcurrency: Int = 8,
    ): Flow<HealthCheckProgress> = channelFlow {
        val total = sites.size
        if (total == 0) return@channelFlow
        val semaphore = Semaphore(maxConcurrency.coerceIn(1, 12))
        var completed = 0
        val lock = Any()

        sites.forEach { site ->
            launch {
                val (status, latency, errorMsg) = semaphore.withPermit {
                    probeSingleSite(site, probeKeyword)
                }
                ruleRepository.updateHealth(
                    id = site.id,
                    status = status,
                    latencyMs = latency,
                    errorMessage = errorMsg,
                )
                val progress = synchronized(lock) {
                    completed += 1
                    HealthCheckProgress(completed, total, site.name)
                }
                send(progress)
            }
        }
    }

    suspend fun probeSingleSite(
        site: SiteRule,
        probeKeyword: String = "ubuntu",
    ): Triple<SiteHealthStatus, Long?, String?> {
        val url = RuleExtractor.buildSearchUrl(site, probeKeyword, 1)
        return when (val res = fetcher.fetch(site, url)) {
            is FetchResult.Success -> {
                val parsed = runCatching {
                    RuleExtractor.parseSearchResponse(site, res.html, res.finalUrl)
                }
                parsed.fold(
                    onSuccess = { items ->
                        if (items.isNotEmpty()) {
                            Triple(SiteHealthStatus.OK, res.latencyMs, "命中 ${items.size} 条")
                        } else {
                            Triple(SiteHealthStatus.NO_RESULTS, res.latencyMs, "HTTP 200 但未解析到条目")
                        }
                    },
                    onFailure = { err ->
                        Triple(SiteHealthStatus.PARSE_ERROR, res.latencyMs, err.message ?: "解析异常")
                    },
                )
            }
            is FetchResult.RedirectToMagnet -> {
                Triple(SiteHealthStatus.PARSE_ERROR, res.latencyMs, "搜索页返回重定向")
            }
            is FetchResult.ChallengeRequired -> {
                Triple(SiteHealthStatus.CF_CHALLENGE, res.latencyMs, "HTTP ${res.statusCode} 需过盾验证")
            }
            is FetchResult.HttpFailure -> {
                Triple(SiteHealthStatus.HTTP_ERROR, res.latencyMs, res.message)
            }
            is FetchResult.NetworkFailure -> {
                Triple(SiteHealthStatus.NETWORK_ERROR, res.latencyMs, res.message)
            }
        }
    }
}

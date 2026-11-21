package com.lanhan.cilicili.ui.sites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanhan.cilicili.AppContainer
import com.lanhan.cilicili.domain.model.RawSiteResult
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSearchStatus
import com.lanhan.cilicili.domain.model.SiteSourceType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.lanhan.cilicili.domain.parser.RuleExtractor
import com.lanhan.cilicili.domain.search.HealthCheckProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SiteFilterTab(val label: String) {
    ALL("全部"),
    ENABLED("已启用"),
    HEALTHY("可用"),
    CF_CHALLENGE("需过盾"),
    FAILED("失效/异常"),
}

sealed interface SitesEffect {
    data class CopyExportJson(val jsonText: String) : SitesEffect
    data class Message(val text: String) : SitesEffect
}

data class SiteInspectState(
    val site: SiteRule,
    val testKeyword: String = "ubuntu",
    val isTesting: Boolean = false,
    val testSummary: String? = null,
    val previewResults: List<RawSiteResult> = emptyList(),
    val challengeUrl: String? = null,
)

data class SitesUiState(
    val allRules: List<SiteRule> = emptyList(),
    val filteredRules: List<SiteRule> = emptyList(),
    val activeFilter: SiteFilterTab = SiteFilterTab.ALL,
    val searchQuery: String = "",
    val isCheckingHealth: Boolean = false,
    val healthProgress: HealthCheckProgress? = null,
    val subscriptionUrl: String = com.lanhan.cilicili.data.local.SettingsRepository.DEFAULT_SUBSCRIPTION_URL,
    val autoSyncSubscription: Boolean = true,
    val lastSubscriptionSyncAt: Long = 0L,
    val lastSubscriptionSyncSummary: String = "",
    val inspectState: SiteInspectState? = null,
    val cfVerifySite: Pair<SiteRule, String>? = null,
)

class SitesViewModel(private val container: AppContainer) : ViewModel() {

    private val _filter = MutableStateFlow(SiteFilterTab.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isChecking = MutableStateFlow(false)
    private val _progress = MutableStateFlow<HealthCheckProgress?>(null)
    private val _inspectState = MutableStateFlow<SiteInspectState?>(null)
    private val _cfVerifySite = MutableStateFlow<Pair<SiteRule, String>?>(null)

    private val _effects = Channel<SitesEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var healthJob: Job? = null

    private val allRulesFlow = container.siteRuleRepository.allRulesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val settingsFlow = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.lanhan.cilicili.data.local.AppSettings())

    val uiState: StateFlow<SitesUiState> = combine(
        allRulesFlow,
        _filter,
        _searchQuery,
        _isChecking,
        _progress,
        settingsFlow,
        _inspectState,
        _cfVerifySite,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allRules = values[0] as List<SiteRule>
        val filter = values[1] as SiteFilterTab
        val query = values[2] as String
        val isChecking = values[3] as Boolean
        val progress = values[4] as HealthCheckProgress?
        val settings = values[5] as com.lanhan.cilicili.data.local.AppSettings
        val inspect = values[6] as SiteInspectState?
        @Suppress("UNCHECKED_CAST")
        val cfVerify = values[7] as Pair<SiteRule, String>?

        val filtered = allRules
            .filter { rule ->
                when (filter) {
                    SiteFilterTab.ALL -> true
                    SiteFilterTab.ENABLED -> rule.enabled
                    SiteFilterTab.HEALTHY -> rule.healthStatus == SiteHealthStatus.OK
                    SiteFilterTab.CF_CHALLENGE -> rule.healthStatus == SiteHealthStatus.CF_CHALLENGE
                    SiteFilterTab.FAILED -> rule.healthStatus in setOf(
                        SiteHealthStatus.HTTP_ERROR,
                        SiteHealthStatus.NETWORK_ERROR,
                        SiteHealthStatus.PARSE_ERROR,
                        SiteHealthStatus.NO_RESULTS,
                    )
                }
            }
            .filter { rule ->
                query.isBlank() ||
                    rule.name.contains(query, ignoreCase = true) ||
                    rule.host.contains(query, ignoreCase = true)
            }

        SitesUiState(
            allRules = allRules,
            filteredRules = filtered,
            activeFilter = filter,
            searchQuery = query,
            isCheckingHealth = isChecking,
            healthProgress = progress,
            subscriptionUrl = settings.subscriptionUrl,
            autoSyncSubscription = settings.autoSyncSubscription,
            lastSubscriptionSyncAt = settings.lastSubscriptionSyncAt,
            lastSubscriptionSyncSummary = settings.lastSubscriptionSyncSummary,
            inspectState = inspect,
            cfVerifySite = cfVerify,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SitesUiState())

    fun setFilter(tab: SiteFilterTab) {
        _filter.value = tab
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun toggleSite(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            container.siteRuleRepository.setEnabled(id, enabled)
        }
    }

    fun runHealthCheck(probeKeyword: String = "ubuntu") {
        val rules = allRulesFlow.value
        if (rules.isEmpty()) return
        healthJob?.cancel()
        _isChecking.value = true
        _progress.value = HealthCheckProgress(0, rules.size, "")

        healthJob = viewModelScope.launch {
            try {
                container.siteHealthChecker.checkAll(
                    sites = rules,
                    probeKeyword = probeKeyword.ifBlank { "ubuntu" },
                    maxConcurrency = settingsFlow.value.maxConcurrency,
                ).collect { p ->
                    _progress.value = p
                }
                _effects.send(SitesEffect.Message("测活完成，共检测 ${rules.size} 个站点"))
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun cancelHealthCheck() {
        healthJob?.cancel()
        _isChecking.value = false
    }

    fun enableOnlyHealthy() {
        viewModelScope.launch {
            val healthyCount = allRulesFlow.value.count { it.healthStatus == SiteHealthStatus.OK }
            if (healthyCount == 0) {
                _effects.send(SitesEffect.Message("暂无标记为「可用」的站点，请先运行一键测活"))
                return@launch
            }
            container.siteRuleRepository.enableOnlyHealthy()
            _effects.send(SitesEffect.Message("已筛选：仅保留 $healthyCount 个可用站点"))
        }
    }

    fun enableAll() {
        viewModelScope.launch {
            container.siteRuleRepository.enableAll()
            _effects.send(SitesEffect.Message("已启用全部站点"))
        }
    }

    fun resetDefaults() {
        viewModelScope.launch {
            val count = container.siteRuleRepository.resetToDefaults(preserveApiSources = true)
            _effects.send(SitesEffect.Message("已重置为内置 $count 条规则（已保留自建 API 节点）"))
        }
    }

    fun setAutoSyncSubscription(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setAutoSyncSubscription(enabled)
        }
    }

    fun importJson(rawJson: String, replaceAll: Boolean) {
        viewModelScope.launch {
            runCatching {
                container.siteRuleRepository.importFromJson(
                    rawJson = rawJson,
                    replaceAll = replaceAll,
                    preserveApiSources = true,
                )
            }.onSuccess { count ->
                _effects.send(SitesEffect.Message("成功导入 $count 条站点规则"))
            }.onFailure { err ->
                _effects.send(SitesEffect.Message("规则 JSON 解析失败: ${err.message}"))
            }
        }
    }

    fun importFromUrl(url: String, replaceAll: Boolean) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        viewModelScope.launch {
            container.settingsRepository.setSubscriptionUrl(cleanUrl)
            val fetched = container.searchOrchestrator.fetchRemoteText(cleanUrl)
            fetched.onSuccess { text ->
                runCatching {
                    container.siteRuleRepository.importFromJson(
                        rawJson = text,
                        replaceAll = replaceAll,
                        preserveApiSources = true,
                        deactivateMissingPublicSites = !replaceAll,
                    )
                }.onSuccess { count ->
                    container.settingsRepository.recordSubscriptionSync(
                        timestampMs = System.currentTimeMillis(),
                        summary = "手动同步 $count 条规则",
                    )
                    _effects.send(SitesEffect.Message("订阅更新成功，共导入 $count 条规则"))
                }.onFailure { err ->
                    container.settingsRepository.recordSubscriptionSyncFailure(
                        "手动同步解析失败: ${err.message ?: "格式错误"}",
                    )
                    _effects.send(SitesEffect.Message("订阅 JSON 格式有误: ${err.message}"))
                }
            }.onFailure { err ->
                container.settingsRepository.recordSubscriptionSyncFailure(
                    "手动同步拉取失败: ${err.message ?: "网络异常"}",
                )
                _effects.send(SitesEffect.Message("拉取订阅失败: ${err.message}"))
            }
        }
    }

    fun exportJson() {
        viewModelScope.launch {
            val text = container.siteRuleRepository.exportToJson()
            _effects.send(SitesEffect.CopyExportJson(text))
        }
    }

    fun openInspect(site: SiteRule) {
        _inspectState.value = SiteInspectState(site = site)
    }

    fun dismissInspect() {
        _inspectState.value = null
    }

    fun updateInspectKeyword(keyword: String) {
        _inspectState.value = _inspectState.value?.copy(testKeyword = keyword)
    }

    fun saveEditedRule(updated: SiteRule) {
        viewModelScope.launch {
            container.siteRuleRepository.upsertRule(updated)
            _inspectState.value = _inspectState.value?.copy(site = updated)
            _effects.send(SitesEffect.Message("已保存「${updated.name}」规则"))
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            container.siteRuleRepository.deleteRule(id)
            _inspectState.value = null
            _effects.send(SitesEffect.Message("已删除该站点规则"))
        }
    }

    fun addApiSource(
        name: String,
        sourceType: SiteSourceType,
        serverUrl: String,
        apiKey: String,
        indexerId: String = "1",
    ) {
        val cleanUrl = serverUrl.trim()
        if (cleanUrl.isEmpty()) return
        viewModelScope.launch {
            val id = container.siteRuleRepository.nextCustomRuleId()
            val sortOrder = container.siteRuleRepository.nextSortOrder()
            val rule = buildApiSourceRule(
                id = id,
                sortOrder = sortOrder,
                name = name,
                sourceType = sourceType,
                serverUrl = cleanUrl,
                apiKey = apiKey,
                indexerId = indexerId,
            )
            container.siteRuleRepository.upsertRule(rule)
            _effects.send(SitesEffect.Message("已添加「${rule.name}」(${sourceType.label})"))
        }
    }

    fun testSingleSite() {
        val current = _inspectState.value ?: return
        val keyword = current.testKeyword.trim().ifEmpty { "ubuntu" }
        _inspectState.value = current.copy(isTesting = true, testSummary = null, challengeUrl = null)

        viewModelScope.launch {
            val (status, items) = container.searchOrchestrator.searchSingleSite(current.site, keyword, 1)
            val summary = when (status) {
                is SiteSearchStatus.Success -> "成功解析 ${status.count} 条结果 (${status.latencyMs}ms)"
                is SiteSearchStatus.ChallengeRequired -> "触发反爬验证 (${status.latencyMs}ms)"
                is SiteSearchStatus.Failed -> "失败: ${status.reason} (${status.latencyMs}ms)"
                else -> "完成"
            }
            val newHealth = when (status) {
                is SiteSearchStatus.Success -> if (items.isNotEmpty()) SiteHealthStatus.OK else SiteHealthStatus.NO_RESULTS
                is SiteSearchStatus.ChallengeRequired -> SiteHealthStatus.CF_CHALLENGE
                is SiteSearchStatus.Failed -> SiteHealthStatus.NETWORK_ERROR
                else -> current.site.healthStatus
            }
            val latency = when (status) {
                is SiteSearchStatus.Success -> status.latencyMs
                is SiteSearchStatus.ChallengeRequired -> status.latencyMs
                is SiteSearchStatus.Failed -> status.latencyMs
                else -> null
            }
            container.siteRuleRepository.updateHealth(current.site.id, newHealth, latency, summary)
            _inspectState.value = _inspectState.value?.copy(
                isTesting = false,
                testSummary = summary,
                previewResults = items.take(8),
                challengeUrl = (status as? SiteSearchStatus.ChallengeRequired)?.challengeUrl,
            )
        }
    }

    fun openCfVerifyForSite(site: SiteRule, urlOverride: String? = null) {
        val targetUrl = urlOverride ?: RuleExtractor.buildSearchUrl(site, "ubuntu", 1)
        _cfVerifySite.value = site to targetUrl
    }

    fun dismissCfVerify() {
        _cfVerifySite.value = null
    }

    fun onCfVerified(host: String, cookieHeader: String, userAgent: String) {
        val (site, _) = _cfVerifySite.value ?: return
        _cfVerifySite.value = null
        container.sessionStore.save(host, cookieHeader, userAgent)

        viewModelScope.launch {
            val (status, latency, msg) = container.siteHealthChecker.probeSingleSite(site)
            container.siteRuleRepository.updateHealth(site.id, status, latency, msg)
            if (_inspectState.value?.site?.id == site.id) {
                testSingleSite()
            } else {
                _effects.send(SitesEffect.Message("「${site.name}」过盾重测：${status.label} ($msg)"))
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SitesViewModel(container) as T
    }

    companion object {
        fun buildApiSourceRule(
            id: Long,
            sortOrder: Int,
            name: String,
            sourceType: SiteSourceType,
            serverUrl: String,
            apiKey: String,
            indexerId: String = "1",
        ): SiteRule {
            val rawUrl = serverUrl.trim().let {
                if (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) {
                    it
                } else {
                    "https://$it"
                }
            }
            val parsed = rawUrl.toHttpUrlOrNull()
            val origin = if (parsed != null) {
                val defaultPort = (parsed.scheme == "http" && parsed.port == 80) ||
                    (parsed.scheme == "https" && parsed.port == 443)
                if (defaultPort) "${parsed.scheme}://${parsed.host}" else "${parsed.scheme}://${parsed.host}:${parsed.port}"
            } else {
                rawUrl.trimEnd('/')
            }

            val cleanKey = apiKey.trim()
            val cleanIndexer = indexerId.trim()
            val searchUrl = if (rawUrl.contains("[keyword]")) {
                rawUrl
            } else {
                val base = rawUrl.trimEnd('/')
                when (sourceType) {
                    SiteSourceType.PROWLARR -> {
                        if (base.endsWith("/api/v1/search")) {
                            "$base?query=[keyword]&type=search&limit=50&offset=[offset]"
                        } else {
                            "$base/api/v1/search?query=[keyword]&type=search&limit=50&offset=[offset]"
                        }
                    }
                    SiteSourceType.TORZNAB -> {
                        val endpoint = when {
                            base.endsWith("/api") -> base
                            cleanIndexer.isNotBlank() -> "$base/$cleanIndexer/api"
                            else -> "$base/api"
                        }
                        val keyParam = if (cleanKey.isNotBlank()) "&apikey=$cleanKey" else ""
                        "$endpoint?t=search&q=[keyword]&limit=50&offset=[offset]$keyParam"
                    }
                    SiteSourceType.HTML,
                    SiteSourceType.JSON_API,
                    SiteSourceType.RSS -> rawUrl
                }
            }

            val headers = buildMap {
                if (cleanKey.isNotBlank()) {
                    put("X-Api-Key", cleanKey)
                }
            }
            val displayName = name.trim().ifEmpty {
                when (sourceType) {
                    SiteSourceType.PROWLARR -> "Prowlarr 聚合"
                    SiteSourceType.TORZNAB -> "Torznab 聚合"
                    SiteSourceType.JSON_API -> "JSON 公开源"
                    SiteSourceType.RSS -> "RSS 订阅源"
                    SiteSourceType.HTML -> "自定义站点"
                }
            }

            return SiteRule(
                id = id,
                name = displayName,
                info = "自建 ${sourceType.label} 后端源",
                enabled = true,
                sortOrder = sortOrder,
                time = "",
                updateTime = "",
                sourceType = sourceType,
                method = "GET",
                mainUrl = origin,
                searchUrl = searchUrl,
                headers = headers,
                groupExpr = "",
                titleExpr = "",
                dateExpr = "",
                sizeExpr = "",
                urlExpr = "",
                magnetExpr = "",
            )
        }
    }
}

package com.lanhan.cilicili.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanhan.cilicili.AppContainer
import com.lanhan.cilicili.data.local.SearchHistoryEntity
import com.lanhan.cilicili.domain.model.AggregatedMagnetItem
import com.lanhan.cilicili.domain.model.DefaultResultAction
import com.lanhan.cilicili.domain.model.MinSizeFilter
import com.lanhan.cilicili.domain.model.RawSiteResult
import com.lanhan.cilicili.domain.model.SiteExecutionState
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSearchStatus
import com.lanhan.cilicili.domain.model.SortOption
import com.lanhan.cilicili.domain.search.DetailResolveResult
import com.lanhan.cilicili.domain.search.SearchOrchestrator
import com.lanhan.cilicili.domain.search.SearchStreamEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CfChallengeTarget(
    val siteId: Long,
    val siteName: String,
    val challengeUrl: String,
    val retryItem: AggregatedMagnetItem? = null,
    val retryAction: MagnetIntentAction? = null,
)

enum class MagnetIntentAction {
    COPY,
    OPEN_DOWNLOADER,
    SHOW_SHEET,
}

sealed interface SearchEffect {
    data class CopyToClipboard(val text: String, val label: String) : SearchEffect
    data class OpenMagnet(val magnetUri: String) : SearchEffect
    data class Message(val text: String) : SearchEffect
}

data class SearchUiState(
    val query: String = "",
    val activeKeyword: String = "",
    val currentPage: Int = 1,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val resolvingDedupKey: String? = null,
    val displayedResults: List<AggregatedMagnetItem> = emptyList(),
    val totalAggregatedCount: Int = 0,
    val siteStates: List<SiteExecutionState> = emptyList(),
    val sortOption: SortOption = SortOption.RELEVANCE,
    val minSizeFilter: MinSizeFilter = MinSizeFilter.ALL,
    val history: List<String> = emptyList(),
    val incognitoMode: Boolean = false,
    val defaultAction: DefaultResultAction = DefaultResultAction.ASK,
    val sheetItem: AggregatedMagnetItem? = null,
    val cfChallengeTarget: CfChallengeTarget? = null,
)

class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _activeKeyword = MutableStateFlow("")
    private val _currentPage = MutableStateFlow(1)
    private val _isSearching = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _resolvingKey = MutableStateFlow<String?>(null)
    private val _rawResults = MutableStateFlow<List<RawSiteResult>>(emptyList())
    private val _siteStates = MutableStateFlow<List<SiteExecutionState>>(emptyList())
    private val _sortOption = MutableStateFlow(SortOption.RELEVANCE)
    private val _minSizeFilter = MutableStateFlow(MinSizeFilter.ALL)
    private val _sheetItem = MutableStateFlow<AggregatedMagnetItem?>(null)
    private val _cfTarget = MutableStateFlow<CfChallengeTarget?>(null)

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val allRulesFlow = container.siteRuleRepository.allRulesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val enabledRulesFlow = container.siteRuleRepository.enabledRulesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val settingsFlow = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.lanhan.cilicili.data.local.AppSettings())
    private val historyFlow = container.searchHistoryDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var searchJob: Job? = null

    val uiState: StateFlow<SearchUiState> = combine(
        _query,
        _activeKeyword,
        _currentPage,
        _isSearching,
        _isLoadingMore,
        _resolvingKey,
        _rawResults,
        _siteStates,
        _sortOption,
        _minSizeFilter,
        historyFlow,
        settingsFlow,
        _sheetItem,
        _cfTarget,
    ) { values ->
        val query = values[0] as String
        val activeKeyword = values[1] as String
        val currentPage = values[2] as Int
        val isSearching = values[3] as Boolean
        val isLoadingMore = values[4] as Boolean
        val resolvingKey = values[5] as String?
        @Suppress("UNCHECKED_CAST")
        val rawResults = values[6] as List<RawSiteResult>
        @Suppress("UNCHECKED_CAST")
        val siteStates = values[7] as List<SiteExecutionState>
        val sortOption = values[8] as SortOption
        val minSizeFilter = values[9] as MinSizeFilter
        @Suppress("UNCHECKED_CAST")
        val historyEntities = values[10] as List<SearchHistoryEntity>
        val settings = values[11] as com.lanhan.cilicili.data.local.AppSettings
        val sheetItem = values[12] as AggregatedMagnetItem?
        val cfTarget = values[13] as CfChallengeTarget?

        val aggregated = SearchOrchestrator.aggregateAndDeduplicate(rawResults)
        val filtered = applyFilterAndSort(aggregated, minSizeFilter, sortOption)

        SearchUiState(
            query = query,
            activeKeyword = activeKeyword,
            currentPage = currentPage,
            isSearching = isSearching,
            isLoadingMore = isLoadingMore,
            resolvingDedupKey = resolvingKey,
            displayedResults = filtered,
            totalAggregatedCount = aggregated.size,
            siteStates = siteStates,
            sortOption = sortOption,
            minSizeFilter = minSizeFilter,
            history = historyEntities.map { it.keyword },
            incognitoMode = settings.incognitoMode,
            defaultAction = settings.defaultAction,
            sheetItem = sheetItem,
            cfChallengeTarget = cfTarget,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun onQueryChange(newValue: String) {
        _query.value = newValue
    }

    fun submitSearch(keywordOverride: String? = null) {
        val keyword = (keywordOverride ?: _query.value).trim()
        if (keyword.isEmpty()) return
        if (keywordOverride != null) _query.value = keyword

        val sites = enabledRulesFlow.value
        if (sites.isEmpty()) {
            viewModelScope.launch {
                _effects.send(SearchEffect.Message("当前没有启用的站点，请先在「站点」页开启或测活"))
            }
            return
        }

        searchJob?.cancel()
        _activeKeyword.value = keyword
        _currentPage.value = 1
        _rawResults.value = emptyList()
        _siteStates.value = sites.map { SiteExecutionState(it, SiteSearchStatus.Pending) }
        _isSearching.value = true
        _isLoadingMore.value = false

        val settings = settingsFlow.value
        if (!settings.incognitoMode) {
            viewModelScope.launch {
                container.searchHistoryDao.upsert(
                    SearchHistoryEntity(keyword = keyword, searchedAt = System.currentTimeMillis()),
                )
            }
        }

        searchJob = viewModelScope.launch {
            try {
                container.searchOrchestrator.searchStreaming(
                    keyword = keyword,
                    page = 1,
                    sites = sites,
                    maxConcurrency = settings.maxConcurrency,
                ).collect { event ->
                    handleStreamEvent(event)
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun loadNextPage() {
        val keyword = _activeKeyword.value
        if (keyword.isBlank() || _isSearching.value || _isLoadingMore.value) return
        val sites = enabledRulesFlow.value
        if (sites.isEmpty()) return

        val nextPage = _currentPage.value + 1
        _currentPage.value = nextPage
        _isLoadingMore.value = true

        searchJob = viewModelScope.launch {
            try {
                container.searchOrchestrator.searchStreaming(
                    keyword = keyword,
                    page = nextPage,
                    sites = sites,
                    maxConcurrency = settingsFlow.value.maxConcurrency,
                ).collect { event ->
                    handleStreamEvent(event)
                }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        _isSearching.value = false
        _isLoadingMore.value = false
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun setMinSizeFilter(filter: MinSizeFilter) {
        _minSizeFilter.value = filter
    }

    fun toggleIncognito() {
        viewModelScope.launch {
            container.settingsRepository.setIncognitoMode(!settingsFlow.value.incognitoMode)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _query.value = ""
        _activeKeyword.value = ""
        _currentPage.value = 1
        _rawResults.value = emptyList()
        _siteStates.value = emptyList()
        _isSearching.value = false
        _isLoadingMore.value = false
    }

    fun deleteHistoryItem(keyword: String) {
        viewModelScope.launch {
            container.searchHistoryDao.delete(keyword)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.searchHistoryDao.clearAll()
        }
    }

    fun onResultClicked(item: AggregatedMagnetItem) {
        when (settingsFlow.value.defaultAction) {
            DefaultResultAction.ASK -> _sheetItem.value = item
            DefaultResultAction.COPY -> executeMagnetAction(item, MagnetIntentAction.COPY)
            DefaultResultAction.OPEN_DOWNLOADER -> executeMagnetAction(item, MagnetIntentAction.OPEN_DOWNLOADER)
        }
    }

    fun dismissSheet() {
        _sheetItem.value = null
    }

    fun executeMagnetAction(item: AggregatedMagnetItem, action: MagnetIntentAction) {
        _sheetItem.value = null
        if (!item.magnetUri.isNullOrBlank()) {
            dispatchResolvedMagnet(item.magnetUri, action)
            return
        }
        viewModelScope.launch {
            _resolvingKey.value = item.dedupKey
            val rulesById = allRulesFlow.value.associateBy { it.id }
            val result = container.searchOrchestrator.resolveMagnet(item, rulesById)
            _resolvingKey.value = null

            when (result) {
                is DetailResolveResult.Success -> {
                    // Cache resolved magnet in rawResults so UI shows it immediately
                    _rawResults.update { list ->
                        list.map { raw ->
                            if (raw.dedupKey == item.dedupKey) {
                                raw.copy(magnetUri = result.magnetUri, infoHash = result.infoHash ?: raw.infoHash)
                            } else {
                                raw
                            }
                        }
                    }
                    dispatchResolvedMagnet(result.magnetUri, action)
                }
                is DetailResolveResult.ChallengeRequired -> {
                    val primary = item.primarySource
                    _cfTarget.value = CfChallengeTarget(
                        siteId = primary.siteId,
                        siteName = primary.siteName,
                        challengeUrl = result.challengeUrl,
                        retryItem = item,
                        retryAction = action,
                    )
                }
                is DetailResolveResult.Failed -> {
                    _effects.send(SearchEffect.Message("提取磁力失败: ${result.reason}"))
                }
            }
        }
    }

    fun openCfChallengeForSite(site: SiteRule, challengeUrl: String) {
        _cfTarget.value = CfChallengeTarget(
            siteId = site.id,
            siteName = site.name,
            challengeUrl = challengeUrl,
        )
    }

    fun dismissCfChallenge() {
        _cfTarget.value = null
    }

    fun onCfVerified(host: String, cookieHeader: String, userAgent: String) {
        val target = _cfTarget.value ?: return
        _cfTarget.value = null
        container.sessionStore.save(host, cookieHeader, userAgent)

        if (target.retryItem != null && target.retryAction != null) {
            executeMagnetAction(target.retryItem, target.retryAction)
            return
        }

        // Retry that single site's search with the new CF session
        val site = allRulesFlow.value.firstOrNull { it.id == target.siteId } ?: return
        val keyword = _activeKeyword.value
        if (keyword.isBlank()) return

        viewModelScope.launch {
            updateSiteState(site, SiteSearchStatus.Running)
            val (status, items) = container.searchOrchestrator.searchSingleSite(site, keyword, _currentPage.value)
            if (items.isNotEmpty()) {
                _rawResults.update { it + items }
            }
            updateSiteState(site, status)
        }
    }

    private fun dispatchResolvedMagnet(magnetUri: String, action: MagnetIntentAction) {
        viewModelScope.launch {
            when (action) {
                MagnetIntentAction.COPY -> {
                    _effects.send(SearchEffect.CopyToClipboard(magnetUri, "已复制磁力链接"))
                }
                MagnetIntentAction.OPEN_DOWNLOADER -> {
                    _effects.send(SearchEffect.OpenMagnet(magnetUri))
                }
                MagnetIntentAction.SHOW_SHEET -> Unit
            }
        }
    }

    private fun handleStreamEvent(event: SearchStreamEvent) {
        when (event) {
            is SearchStreamEvent.SiteUpdated -> updateSiteState(event.state.site, event.state.status)
            is SearchStreamEvent.ResultsBatch -> _rawResults.update { it + event.items }
        }
    }

    private fun updateSiteState(site: SiteRule, status: SiteSearchStatus) {
        _siteStates.update { list ->
            val idx = list.indexOfFirst { it.site.id == site.id }
            if (idx >= 0) {
                list.toMutableList().apply { this[idx] = SiteExecutionState(site, status) }
            } else {
                list + SiteExecutionState(site, status)
            }
        }
    }

    private fun applyFilterAndSort(
        items: List<AggregatedMagnetItem>,
        minSizeFilter: MinSizeFilter,
        sortOption: SortOption,
    ): List<AggregatedMagnetItem> {
        val filtered = if (minSizeFilter.minBytes > 0) {
            items.filter { it.sizeBytes >= minSizeFilter.minBytes }
        } else {
            items
        }
        return when (sortOption) {
            SortOption.RELEVANCE -> filtered
            SortOption.SEEDERS_DESC -> filtered.sortedByDescending { it.seeders ?: -1 }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> filtered.sortedBy { if (it.sizeBytes < 0) Long.MAX_VALUE else it.sizeBytes }
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.dateText }
            SortOption.SOURCES_DESC -> filtered.sortedByDescending { it.sources.size }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(container) as T
    }
}

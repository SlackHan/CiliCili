package com.lanhan.cilicili

import android.app.Application
import com.lanhan.cilicili.data.local.CiliDatabase
import com.lanhan.cilicili.data.local.SettingsRepository
import com.lanhan.cilicili.data.local.SiteRuleRepository
import com.lanhan.cilicili.data.network.HttpClientFactory
import com.lanhan.cilicili.data.network.SiteHttpFetcher
import com.lanhan.cilicili.data.network.SiteSessionStore
import com.lanhan.cilicili.domain.model.DohProvider
import com.lanhan.cilicili.domain.search.SearchOrchestrator
import com.lanhan.cilicili.domain.search.SiteHealthChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: CiliDatabase = CiliDatabase.build(application)
    val settingsRepository = SettingsRepository(application)
    val siteRuleRepository = SiteRuleRepository(application, database.siteRuleDao())
    val searchHistoryDao = database.searchHistoryDao()
    val sessionStore = SiteSessionStore(application)

    @Volatile
    private var currentClient: OkHttpClient = HttpClientFactory.create()

    @Volatile
    private var lastConfig: Pair<DohProvider, Int> = DohProvider.SYSTEM to 12

    val fetcher = SiteHttpFetcher(
        clientProvider = { currentClient },
        sessionStore = sessionStore,
    )
    val searchOrchestrator = SearchOrchestrator(fetcher)
    val siteHealthChecker = SiteHealthChecker(fetcher, siteRuleRepository)

    init {
        appScope.launch {
            siteRuleRepository.ensureSeeded()
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.builtInPublicSourcesSeeded) {
                siteRuleRepository.seedMissingPublicSources()
                settingsRepository.markBuiltInPublicSourcesSeeded()
            }
            if (settings.autoSyncSubscription && settings.subscriptionUrl.isNotBlank()) {
                val intervalMs = settings.subscriptionSyncIntervalHours.coerceAtLeast(1) * 3_600_000L
                if (System.currentTimeMillis() - settings.lastSubscriptionSyncAt >= intervalMs) {
                    searchOrchestrator.fetchRemoteText(settings.subscriptionUrl).onSuccess { text ->
                        runCatching {
                            siteRuleRepository.importFromJson(
                                rawJson = text,
                                replaceAll = false,
                                preserveApiSources = true,
                            )
                        }.onSuccess { count ->
                            settingsRepository.recordSubscriptionSync(
                                timestampMs = System.currentTimeMillis(),
                                summary = "自动同步 $count 条规则",
                            )
                        }.onFailure { err ->
                            settingsRepository.recordSubscriptionSyncFailure(
                                "自动同步解析失败: ${err.message ?: "格式错误"}",
                            )
                        }
                    }.onFailure { err ->
                        settingsRepository.recordSubscriptionSyncFailure(
                            "自动同步拉取失败: ${err.message ?: "网络异常"}",
                        )
                    }
                }
            }
        }
        appScope.launch {
            settingsRepository.settingsFlow
                .map { it.dohProvider to it.timeoutSeconds }
                .distinctUntilChanged()
                .collect { (doh, timeout) ->
                    if (lastConfig != (doh to timeout)) {
                        lastConfig = doh to timeout
                        currentClient = HttpClientFactory.create(doh, timeout)
                    }
                }
        }
    }
}

class CiliApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

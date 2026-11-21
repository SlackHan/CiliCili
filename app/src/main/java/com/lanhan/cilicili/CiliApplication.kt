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

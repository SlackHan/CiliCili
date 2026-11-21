package com.lanhan.cilicili.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lanhan.cilicili.domain.model.DefaultResultAction
import com.lanhan.cilicili.domain.model.DohProvider
import com.lanhan.cilicili.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cilicili_settings")

data class AppSettings(
    val dohProvider: DohProvider = DohProvider.SYSTEM,
    val maxConcurrency: Int = 6,
    val timeoutSeconds: Int = 12,
    val incognitoMode: Boolean = false,
    val defaultAction: DefaultResultAction = DefaultResultAction.ASK,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val subscriptionUrl: String = SettingsRepository.DEFAULT_SUBSCRIPTION_URL,
    val autoSyncSubscription: Boolean = true,
    val subscriptionSyncIntervalHours: Int = 24,
    val lastSubscriptionSyncAt: Long = 0L,
    val lastSubscriptionSyncSummary: String = "",
    val builtInPublicSourcesSeeded: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DOH_PROVIDER = stringPreferencesKey("doh_provider")
        val MAX_CONCURRENCY = intPreferencesKey("max_concurrency")
        val TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val DEFAULT_ACTION = stringPreferencesKey("default_action")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SUBSCRIPTION_URL = stringPreferencesKey("subscription_url")
        val AUTO_SYNC_SUBSCRIPTION = booleanPreferencesKey("auto_sync_subscription")
        val LAST_SUBSCRIPTION_SYNC_AT = longPreferencesKey("last_subscription_sync_at")
        val LAST_SUBSCRIPTION_SYNC_SUMMARY = stringPreferencesKey("last_subscription_sync_summary")
        val BUILT_IN_PUBLIC_SOURCES_SEEDED = booleanPreferencesKey("built_in_public_sources_seeded_v1")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            dohProvider = prefs[Keys.DOH_PROVIDER]
                ?.let { runCatching { DohProvider.valueOf(it) }.getOrNull() }
                ?: DohProvider.SYSTEM,
            maxConcurrency = (prefs[Keys.MAX_CONCURRENCY] ?: 6).coerceIn(2, 12),
            timeoutSeconds = (prefs[Keys.TIMEOUT_SECONDS] ?: 12).coerceIn(5, 30),
            incognitoMode = prefs[Keys.INCOGNITO_MODE] ?: false,
            defaultAction = prefs[Keys.DEFAULT_ACTION]
                ?.let { runCatching { DefaultResultAction.valueOf(it) }.getOrNull() }
                ?: DefaultResultAction.ASK,
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            subscriptionUrl = prefs[Keys.SUBSCRIPTION_URL] ?: DEFAULT_SUBSCRIPTION_URL,
            autoSyncSubscription = prefs[Keys.AUTO_SYNC_SUBSCRIPTION] ?: true,
            subscriptionSyncIntervalHours = 24,
            lastSubscriptionSyncAt = prefs[Keys.LAST_SUBSCRIPTION_SYNC_AT] ?: 0L,
            lastSubscriptionSyncSummary = prefs[Keys.LAST_SUBSCRIPTION_SYNC_SUMMARY].orEmpty(),
            builtInPublicSourcesSeeded = prefs[Keys.BUILT_IN_PUBLIC_SOURCES_SEEDED] ?: false,
        )
    }

    suspend fun setDohProvider(provider: DohProvider) {
        context.dataStore.edit { it[Keys.DOH_PROVIDER] = provider.name }
    }

    suspend fun setMaxConcurrency(value: Int) {
        context.dataStore.edit { it[Keys.MAX_CONCURRENCY] = value.coerceIn(2, 12) }
    }

    suspend fun setTimeoutSeconds(value: Int) {
        context.dataStore.edit { it[Keys.TIMEOUT_SECONDS] = value.coerceIn(5, 30) }
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INCOGNITO_MODE] = enabled }
    }

    suspend fun setDefaultAction(action: DefaultResultAction) {
        context.dataStore.edit { it[Keys.DEFAULT_ACTION] = action.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setSubscriptionUrl(url: String) {
        context.dataStore.edit { it[Keys.SUBSCRIPTION_URL] = url.trim() }
    }

    suspend fun setAutoSyncSubscription(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC_SUBSCRIPTION] = enabled }
    }

    suspend fun recordSubscriptionSync(timestampMs: Long, summary: String) {
        context.dataStore.edit {
            it[Keys.LAST_SUBSCRIPTION_SYNC_AT] = timestampMs
            it[Keys.LAST_SUBSCRIPTION_SYNC_SUMMARY] = summary
        }
    }

    suspend fun recordSubscriptionSyncFailure(summary: String) {
        context.dataStore.edit {
            it[Keys.LAST_SUBSCRIPTION_SYNC_SUMMARY] = summary
        }
    }

    suspend fun markBuiltInPublicSourcesSeeded() {
        context.dataStore.edit { it[Keys.BUILT_IN_PUBLIC_SOURCES_SEEDED] = true }
    }

    companion object {
        const val OFFICIAL_GITHUB_RAW_SUBSCRIPTION_URL =
            "https://raw.githubusercontent.com/SlackHan/CiliCili/main/rules/sites_live.json"
        const val OFFICIAL_CDN_SUBSCRIPTION_URL =
            "https://fastly.jsdelivr.net/gh/SlackHan/CiliCili@main/rules/sites_live.json"
        const val DEFAULT_SUBSCRIPTION_URL = OFFICIAL_GITHUB_RAW_SUBSCRIPTION_URL

        val OFFICIAL_SUBSCRIPTION_PRESETS: List<Pair<String, String>> = listOf(
            "填入官方源 (GitHub Raw)" to OFFICIAL_GITHUB_RAW_SUBSCRIPTION_URL,
            "填入官方源 (CDN 镜像)" to OFFICIAL_CDN_SUBSCRIPTION_URL,
        )
    }
}

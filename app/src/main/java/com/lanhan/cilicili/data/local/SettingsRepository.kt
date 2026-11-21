package com.lanhan.cilicili.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    val subscriptionUrl: String = "",
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
            subscriptionUrl = prefs[Keys.SUBSCRIPTION_URL].orEmpty(),
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
}

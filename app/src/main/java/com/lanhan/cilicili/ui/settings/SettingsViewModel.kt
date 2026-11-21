package com.lanhan.cilicili.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanhan.cilicili.AppContainer
import com.lanhan.cilicili.data.local.AppSettings
import com.lanhan.cilicili.domain.model.DefaultResultAction
import com.lanhan.cilicili.domain.model.DohProvider
import com.lanhan.cilicili.domain.model.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val verifiedCfHostCount: Int = 0,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.settingsFlow,
        container.sessionStore.verifiedHostsFlow,
    ) { settings, hosts ->
        SettingsUiState(
            settings = settings,
            verifiedCfHostCount = hosts.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setDohProvider(provider: DohProvider) {
        viewModelScope.launch { container.settingsRepository.setDohProvider(provider) }
    }

    fun setMaxConcurrency(value: Int) {
        viewModelScope.launch { container.settingsRepository.setMaxConcurrency(value) }
    }

    fun setTimeoutSeconds(value: Int) {
        viewModelScope.launch { container.settingsRepository.setTimeoutSeconds(value) }
    }

    fun setIncognitoMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setIncognitoMode(enabled) }
    }

    fun setDefaultAction(action: DefaultResultAction) {
        viewModelScope.launch { container.settingsRepository.setDefaultAction(action) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setDynamicColor(enabled) }
    }

    fun clearCfSessions() {
        container.sessionStore.clearAll()
        viewModelScope.launch { _messages.send("已清除全部站点过盾 Cookie 缓存") }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            container.searchHistoryDao.clearAll()
            _messages.send("已清空本地搜索历史")
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container) as T
    }
}

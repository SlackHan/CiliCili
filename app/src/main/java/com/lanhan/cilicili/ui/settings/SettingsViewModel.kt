package com.lanhan.cilicili.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanhan.cilicili.AppContainer
import com.lanhan.cilicili.BuildConfig
import com.lanhan.cilicili.data.local.AppSettings
import com.lanhan.cilicili.domain.model.DefaultResultAction
import com.lanhan.cilicili.domain.model.DohProvider
import com.lanhan.cilicili.domain.model.ThemeMode
import com.lanhan.cilicili.domain.update.AppUpdateChecker
import com.lanhan.cilicili.domain.update.ReleaseInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val verifiedCfHostCount: Int = 0,
    val historyCount: Int = 0,
    val isCheckingUpdate: Boolean = false,
    val latestRelease: ReleaseInfo? = null,
)

class SettingsViewModel(
    private val container: AppContainer,
    private val updateChecker: AppUpdateChecker = AppUpdateChecker(container.fetcher),
) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    private val _latestRelease = MutableStateFlow<ReleaseInfo?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.settingsFlow,
        container.sessionStore.verifiedHostsFlow,
        container.searchHistoryDao.observeRecent(),
        _isCheckingUpdate,
        _latestRelease,
    ) { settings, hosts, history, isCheckingUpdate, latestRelease ->
        SettingsUiState(
            settings = settings,
            verifiedCfHostCount = hosts.size,
            historyCount = history.size,
            isCheckingUpdate = isCheckingUpdate,
            latestRelease = latestRelease,
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

    fun checkForUpdate() {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            try {
                val currentVersion = BuildConfig.VERSION_NAME
                updateChecker.checkForUpdate(currentVersion)
                    .onSuccess { release ->
                        if (release.hasUpdate) {
                            _latestRelease.value = release
                        } else {
                            _messages.send("当前已是最新版本 (v$currentVersion)")
                        }
                    }
                    .onFailure { err ->
                        _messages.send("检查更新失败: ${err.message ?: "网络异常"}")
                    }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _latestRelease.value = null
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container) as T
    }
}

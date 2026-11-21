package com.lanhan.cilicili.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanhan.cilicili.BuildConfig
import com.lanhan.cilicili.domain.model.DefaultResultAction
import com.lanhan.cilicili.domain.model.DohProvider
import com.lanhan.cilicili.domain.model.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        // Network & DoH
        SettingsCard(title = "网络与防污染") {
            Text("DNS-over-HTTPS (DoH)", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DohProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = settings.dohProvider == provider,
                        onClick = { viewModel.setDohProvider(provider) },
                        label = { Text(provider.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "最大并发站点数: ${settings.maxConcurrency}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = settings.maxConcurrency.toFloat(),
                onValueChange = { viewModel.setMaxConcurrency(it.roundToInt()) },
                valueRange = 2f..12f,
                steps = 9,
            )
            Text(
                text = "单站请求超时: ${settings.timeoutSeconds} 秒",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = settings.timeoutSeconds.toFloat(),
                onValueChange = { viewModel.setTimeoutSeconds(it.roundToInt()) },
                valueRange = 5f..30f,
                steps = 24,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已缓存过盾凭证: ${uiState.verifiedCfHostCount} 个域名",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = viewModel::clearCfSessions,
                    enabled = uiState.verifiedCfHostCount > 0,
                ) {
                    Text("清除凭证")
                }
            }
        }

        // Interaction
        SettingsCard(title = "交互与操作") {
            Text("点击搜索结果卡片时的默认动作", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DefaultResultAction.entries.forEach { action ->
                    FilterChip(
                        selected = settings.defaultAction == action,
                        onClick = { viewModel.setDefaultAction(action) },
                        label = { Text(action.label) },
                    )
                }
            }
        }

        // Privacy
        SettingsCard(title = "隐私与历史") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("无痕搜索模式", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "开启后不再记录搜索历史关键词",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.incognitoMode,
                    onCheckedChange = viewModel::setIncognitoMode,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::clearSearchHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清空全部搜索历史")
            }
        }

        // Theme
        SettingsCard(title = "外观") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Material You 动态取色", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }
        }

        // About & Permission Audit
        SettingsCard(title = "关于 CiliCili") {
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "本应用仅声明单一「android.permission.INTERNET」网络权限。无广告 SDK、无统计埋点、无应用内更新弹窗、无后台常驻服务，所有搜索历史与规则完全保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

package com.lanhan.cilicili.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanhan.cilicili.BuildConfig
import com.lanhan.cilicili.domain.model.DefaultResultAction
import com.lanhan.cilicili.domain.model.DohProvider
import com.lanhan.cilicili.domain.model.ThemeMode
import com.lanhan.cilicili.ui.components.InstrumentHairline
import com.lanhan.cilicili.ui.components.InstrumentSegmentedControl
import com.lanhan.cilicili.ui.components.InstrumentSwitch
import com.lanhan.cilicili.ui.components.InstrumentTopBar
import com.lanhan.cilicili.ui.components.VernierRulerSlider
import com.lanhan.cilicili.ui.theme.InstrumentTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val c = InstrumentTheme.colors

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        InstrumentTopBar(
            leading = {
                Text(
                    text = "设置",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 01 · DNS-over-HTTPS (2x2 Matrix)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "01 · DNS-over-HTTPS",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.muted,
                    )
                    Text(
                        text = settings.dohProvider.name,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = c.accent,
                    )
                }

                val rows = DohProvider.entries.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { provider ->
                                val selected = settings.dohProvider == provider
                                val subText = provider.bootstrapIps.firstOrNull() ?: "系统解析"
                                val title = when (provider) {
                                    DohProvider.SYSTEM -> "系统默认"
                                    DohProvider.ALIDNS -> "阿里 AliDNS"
                                    DohProvider.CLOUDFLARE -> "Cloudflare"
                                    DohProvider.QUAD9 -> "Quad9"
                                }
                                Surface(
                                    onClick = { viewModel.setDohProvider(provider) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) c.accent.copy(alpha = 0.10f) else c.panel,
                                    border = BorderStroke(1.dp, if (selected) c.accent else c.hairline),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = c.text,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (selected) c.accent else c.tick),
                                            )
                                        }
                                        Text(
                                            text = subText,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = c.muted,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            InstrumentHairline()

            // 02 · Engine Concurrency & Timeout (Vernier Rulers)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "02 · 引擎并发",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                )

                VernierRulerSlider(
                    label = "最大并发站点数",
                    valueText = settings.maxConcurrency.toString().padStart(2, '0'),
                    value = settings.maxConcurrency,
                    valueRange = 2..12,
                    majorTicks = listOf(2 to "02", 7 to "07", 12 to "12"),
                    minorIntervals = 10,
                    onValueChange = viewModel::setMaxConcurrency,
                )

                VernierRulerSlider(
                    label = "单站请求超时",
                    valueText = "${settings.timeoutSeconds}s",
                    value = settings.timeoutSeconds,
                    valueRange = 5..30,
                    majorTicks = listOf(
                        5 to "5s",
                        10 to "10s",
                        15 to "15s",
                        20 to "20s",
                        25 to "25s",
                        30 to "30s",
                    ),
                    minorIntervals = 10,
                    onValueChange = viewModel::setTimeoutSeconds,
                )
            }

            InstrumentHairline()

            // 03 · Interaction & Appearance (3-Way Physical Switches)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "03 · 交互与外观",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "点击搜索结果默认动作",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.text,
                    )
                    InstrumentSegmentedControl(
                        options = listOf(
                            DefaultResultAction.ASK to "询问",
                            DefaultResultAction.COPY to "复制",
                            DefaultResultAction.OPEN_DOWNLOADER to "下载",
                        ),
                        selected = settings.defaultAction,
                        onSelect = viewModel::setDefaultAction,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "外观材质",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.text,
                    )
                    InstrumentSegmentedControl(
                        options = listOf(
                            ThemeMode.SYSTEM to "跟随",
                            ThemeMode.DARK to "石墨",
                            ThemeMode.LIGHT to "陶瓷",
                        ),
                        selected = settings.themeMode,
                        onSelect = viewModel::setThemeMode,
                    )
                }
            }

            InstrumentHairline()

            // 04 · Cache & Privacy
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "04 · 缓存与隐私",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = c.panel,
                    border = BorderStroke(1.dp, c.hairline),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Incognito toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "无痕检索",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = c.text,
                                )
                                Text(
                                    text = "不记录检索历史关键词",
                                    fontSize = 12.sp,
                                    color = c.muted,
                                )
                            }
                            InstrumentSwitch(
                                checked = settings.incognitoMode,
                                onCheckedChange = viewModel::setIncognitoMode,
                            )
                        }

                        InstrumentHairline()

                        // CF Session Cache
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "过盾凭证缓存",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = c.text,
                                )
                                Text(
                                    text = "${uiState.verifiedCfHostCount} 个域名",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.muted,
                                )
                            }
                            Surface(
                                onClick = viewModel::clearCfSessions,
                                enabled = uiState.verifiedCfHostCount > 0,
                                shape = RoundedCornerShape(6.dp),
                                color = c.chip,
                                border = BorderStroke(1.dp, c.hairline),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "清除",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (uiState.verifiedCfHostCount > 0) c.text else c.muted,
                                    )
                                }
                            }
                        }

                        InstrumentHairline()

                        // History clear
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "本地检索历史",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = c.text,
                                )
                                Text(
                                    text = "${uiState.historyCount} 条记录",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.muted,
                                )
                            }
                            Surface(
                                onClick = viewModel::clearSearchHistory,
                                enabled = uiState.historyCount > 0,
                                shape = RoundedCornerShape(6.dp),
                                color = c.chip,
                                border = BorderStroke(1.dp, c.hairline),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "清空",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (uiState.historyCount > 0) c.text else c.muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Minimal single-line version footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "CiliCili v${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

package com.lanhan.cilicili.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanhan.cilicili.BuildConfig
import com.lanhan.cilicili.domain.model.DefaultResultAction
import com.lanhan.cilicili.domain.model.DohProvider
import com.lanhan.cilicili.domain.model.ThemeMode
import com.lanhan.cilicili.domain.update.ReleaseInfo
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
    val context = LocalContext.current

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

            InstrumentHairline()

            // 05 · Version & Update Nameplate Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "05 · 版本与更新",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                )

                Surface(
                    onClick = viewModel::checkForUpdate,
                    enabled = !uiState.isCheckingUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = c.panel,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (uiState.isCheckingUpdate) c.accent.copy(alpha = 0.65f) else c.hairline,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (uiState.isCheckingUpdate) c.accent else c.ok),
                                )
                                Text(
                                    text = "CiliCili v${BuildConfig.VERSION_NAME}",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = if (uiState.isCheckingUpdate) {
                                    "正在连接 GitHub Releases 通道..."
                                } else {
                                    "BUILD #${BuildConfig.VERSION_CODE} · GitHub Releases 更新通道"
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = c.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Surface(
                            onClick = viewModel::checkForUpdate,
                            enabled = !uiState.isCheckingUpdate,
                            shape = RoundedCornerShape(6.dp),
                            color = if (uiState.isCheckingUpdate) c.accent.copy(alpha = 0.12f) else c.chip,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (uiState.isCheckingUpdate) c.accent else c.hairline,
                            ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Crossfade(
                                    targetState = uiState.isCheckingUpdate,
                                    animationSpec = tween(140),
                                    label = "checkUpdateCrossfade",
                                ) { checking ->
                                    if (checking) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(11.dp),
                                                strokeWidth = 1.6.dp,
                                                color = c.accent,
                                            )
                                            Text(
                                                text = "检查中",
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                color = c.accent,
                                                maxLines = 1,
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "检查更新",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = c.text,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    uiState.latestRelease?.let { release ->
        ReleaseUpdateDialog(
            release = release,
            onDismiss = viewModel::dismissUpdateDialog,
            onDownload = { targetRelease ->
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetRelease.downloadUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                viewModel.dismissUpdateDialog()
            },
        )
    }
}

@Composable
private fun ReleaseUpdateDialog(
    release: ReleaseInfo,
    onDismiss: () -> Unit,
    onDownload: (ReleaseInfo) -> Unit,
) {
    val c = InstrumentTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = c.panel,
            border = BorderStroke(1.dp, c.hairline),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(c.accent),
                            )
                            Text(
                                text = "发现新版本",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = c.text,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.accent.copy(alpha = 0.14f))
                                .border(1.dp, c.accent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "v${release.versionName}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = c.accent,
                            )
                        }
                    }

                    Text(
                        text = buildString {
                            append("当前 v${BuildConfig.VERSION_NAME} → 最新 ${release.tagName}")
                            if (release.publishedAt.length >= 10) {
                                append(" · ${release.publishedAt.take(10)}")
                            }
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.muted,
                    )

                    if (release.title.isNotBlank() && release.title != release.tagName) {
                        Text(
                            text = release.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                InstrumentHairline()

                // Changelog Scroll Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "CHANGELOG · 变更日志",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.muted,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 84.dp, max = 240.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.bg)
                            .border(1.dp, c.hairline, RoundedCornerShape(6.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            text = release.changelog,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c.text,
                            lineHeight = 18.sp,
                        )
                    }
                }

                InstrumentHairline()

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = c.chip,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "稍后",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = c.text,
                            )
                        }
                    }

                    Surface(
                        onClick = { onDownload(release) },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(6.dp),
                        color = c.accent,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "前往下载 APK",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = c.onAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

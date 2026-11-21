package com.lanhan.cilicili.ui.sites

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSourceType
import com.lanhan.cilicili.ui.components.CloudflareVerifyDialog
import com.lanhan.cilicili.ui.components.InstrumentHairline
import com.lanhan.cilicili.ui.components.InstrumentSwitch
import com.lanhan.cilicili.ui.components.InstrumentTopBar
import com.lanhan.cilicili.ui.theme.InstrumentTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(
    viewModel: SitesViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = InstrumentTheme.colors

    var showMoreMenu by remember { mutableStateOf(false) }
    var showHealthDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var showAddApiDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SitesEffect.CopyExportJson -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("cilicili_rules", effect.jsonText))
                    snackbarHostState.showSnackbar("已导出全部规则 JSON 到剪贴板")
                }
                is SitesEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    val totalCount = state.allRules.size
    val enabledCount = state.allRules.count { it.enabled }
    val healthyCount = state.allRules.count { it.healthStatus == SiteHealthStatus.OK }
    val cfCount = state.allRules.count { it.healthStatus == SiteHealthStatus.CF_CHALLENGE }
    val otherCount = (totalCount - healthyCount - cfCount).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        // Top Strip
        InstrumentTopBar(
            leading = {
                Text(
                    text = "节点",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                )
            },
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { showAddApiDialog = true },
                        shape = RoundedCornerShape(6.dp),
                        color = c.chip,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+",
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = c.text,
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            if (state.isCheckingHealth) viewModel.cancelHealthCheck() else showHealthDialog = true
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (state.isCheckingHealth) c.activeBg else c.accent,
                    ) {
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isCheckingHealth) c.accent else c.onAccent),
                            )
                            Text(
                                text = if (state.isCheckingHealth) "停止" else "全部测活",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.isCheckingHealth) c.activeText else c.onAccent,
                            )
                        }
                    }
                }
            },
        )

        // Global Health Summary + Filter Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = healthyCount.toString().padStart(2, '0'),
                        fontSize = 26.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = c.text,
                    )
                    Text(
                        text = "/ ${totalCount.toString().padStart(2, '0')} 可用 · 启用 $enabledCount",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.muted,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }

                // Shape + Color Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(c.ok))
                        Text("$healthyCount", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.text)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).rotate(45f).background(c.warn))
                        Text("$cfCount", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.text)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .border(1.dp, c.muted, RoundedCornerShape(1.dp)),
                        )
                        Text("$otherCount", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.muted)
                    }
                }
            }

            // 6dp Segmented Proportional Health Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.track),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (healthyCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(healthyCount.toFloat())
                            .background(c.ok),
                    )
                }
                if (cfCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(cfCount.toFloat())
                            .background(c.warn),
                    )
                }
                if (otherCount > 0 || totalCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(otherCount.coerceAtLeast(1).toFloat())
                            .background(c.fail),
                    )
                }
            }

            // Live Health Check Progress
            state.healthProgress?.takeIf { state.isCheckingHealth }?.let { prog ->
                val fraction = if (prog.total > 0) prog.completed.toFloat() / prog.total else 0f
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "正在探测 ${prog.completed}/${prog.total} · ${prog.lastCheckedSite}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(fraction * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c.accent,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = c.accent,
                        trackColor = c.track,
                    )
                }
            }

            // Compact Search Input + More Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    color = c.panel,
                    border = BorderStroke(1.dp, c.hairline),
                ) {
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "/",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c.muted,
                        )
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = c.text),
                            cursorBrush = SolidColor(c.accent),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (state.searchQuery.isEmpty()) {
                                        Text("过滤站点或域名...", fontSize = 13.sp, color = c.muted)
                                    }
                                    inner()
                                }
                            },
                        )
                        if (state.searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { viewModel.setSearchQuery("") },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("×", fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = c.muted)
                            }
                        }
                    }
                }

                Box {
                    Surface(
                        onClick = { showMoreMenu = true },
                        shape = RoundedCornerShape(6.dp),
                        color = c.chip,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⋮", fontSize = 16.sp, fontFamily = FontFamily.Monospace, color = c.text)
                        }
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("添加 Prowlarr / Torznab 源") },
                            onClick = {
                                showMoreMenu = false
                                showAddApiDialog = true
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("仅启用可用站点") },
                            onClick = {
                                showMoreMenu = false
                                viewModel.enableOnlyHealthy()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("全部启用") },
                            onClick = {
                                showMoreMenu = false
                                viewModel.enableAll()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("导入 JSON 规则") },
                            onClick = {
                                showMoreMenu = false
                                showImportJsonDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("远程订阅更新") },
                            onClick = {
                                showMoreMenu = false
                                showSubscriptionDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("导出 JSON 到剪贴板") },
                            onClick = {
                                showMoreMenu = false
                                viewModel.exportJson()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("重置为内置规则") },
                            onClick = {
                                showMoreMenu = false
                                viewModel.resetDefaults()
                            },
                        )
                    }
                }
            }

            // 5-State Filter Tabs (Neutral High-Contrast Active)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SiteFilterTab.entries.forEach { tab ->
                    val selected = state.activeFilter == tab
                    val shortTabLabel = when (tab) {
                        SiteFilterTab.ALL -> "全部"
                        SiteFilterTab.ENABLED -> "启用"
                        SiteFilterTab.HEALTHY -> "可用"
                        SiteFilterTab.CF_CHALLENGE -> "过盾"
                        SiteFilterTab.FAILED -> "失效"
                    }
                    Surface(
                        onClick = { viewModel.setFilter(tab) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) c.activeBg else c.panel,
                        border = BorderStroke(1.dp, if (selected) c.activeBg else c.hairline),
                    ) {
                        Box(
                            modifier = Modifier.height(30.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = shortTabLabel,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) c.activeText else c.text,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        InstrumentHairline()

        // Node Telemetry Rows
        if (state.filteredRules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "没有匹配的节点",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(state.filteredRules, key = { it.id }) { rule ->
                    NodeTelemetryRow(
                        rule = rule,
                        onToggle = { enabled -> viewModel.toggleSite(rule.id, enabled) },
                        onClick = { viewModel.openInspect(rule) },
                        onVerifyCf = { viewModel.openCfVerifyForSite(rule) },
                    )
                    InstrumentHairline()
                }
            }
        }
    }

    // Dialog: Start Health Check with custom probe keyword
    if (showHealthDialog) {
        var keyword by remember { mutableStateOf("ubuntu") }
        AlertDialog(
            onDismissRequest = { showHealthDialog = false },
            title = { Text("批量测活") },
            text = {
                Column {
                    Text(
                        text = "并发探测全部 $totalCount 个站点，检测域名连通性、反爬状态与规则解析。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("测试关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHealthDialog = false
                        viewModel.runHealthCheck(keyword)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accent,
                        contentColor = c.onAccent,
                    ),
                ) {
                    Text("开始测活")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHealthDialog = false }) { Text("取消") }
            },
        )
    }

    // Dialog: Import JSON Rules
    if (showImportJsonDialog) {
        var rawText by remember { mutableStateOf("") }
        var replaceAll by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            title = { Text("导入站点规则 JSON") },
            text = {
                Column {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("粘贴 site JSON 数组或单条对象") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { replaceAll = !replaceAll },
                    ) {
                        Checkbox(checked = replaceAll, onCheckedChange = { replaceAll = it })
                        Text("覆盖现有公开规则（自动保留 Prowlarr / Torznab 节点）", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportJsonDialog = false
                        viewModel.importJson(rawText, replaceAll)
                    },
                    enabled = rawText.isNotBlank(),
                ) {
                    Text("导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportJsonDialog = false }) { Text("取消") }
            },
        )
    }

    // Dialog: Remote Subscription URL
    if (showSubscriptionDialog) {
        var url by remember(state.subscriptionUrl) { mutableStateOf(state.subscriptionUrl) }
        var replaceAll by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSubscriptionDialog = false },
            title = { Text("远程规则订阅") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("JSON Raw URL (如 GitHub sites_live.json)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { replaceAll = !replaceAll },
                    ) {
                        Checkbox(checked = replaceAll, onCheckedChange = { replaceAll = it })
                        Text("覆盖公开规则（自动保留 Prowlarr / Torznab 节点）", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            viewModel.setAutoSyncSubscription(!state.autoSyncSubscription)
                        },
                    ) {
                        Checkbox(
                            checked = state.autoSyncSubscription,
                            onCheckedChange = viewModel::setAutoSyncSubscription,
                        )
                        Text("启动时自动同步（每 24 小时）", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.lastSubscriptionSyncSummary.isNotBlank()) {
                        val timePrefix = if (state.lastSubscriptionSyncAt > 0L) {
                            val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                            "${fmt.format(java.util.Date(state.lastSubscriptionSyncAt))} · "
                        } else {
                            ""
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "上次状态：$timePrefix${state.lastSubscriptionSyncSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubscriptionDialog = false
                        viewModel.importFromUrl(url, replaceAll)
                    },
                    enabled = url.isNotBlank(),
                ) {
                    Text("立即拉取")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubscriptionDialog = false }) { Text("取消") }
            },
        )
    }

    // Dialog: Add Prowlarr / Torznab Source
    if (showAddApiDialog) {
        AddApiSourceDialog(
            onDismiss = { showAddApiDialog = false },
            onConfirm = { name, type, url, apiKey, indexerId ->
                showAddApiDialog = false
                viewModel.addApiSource(name, type, url, apiKey, indexerId)
            },
        )
    }

    // Bottom Sheet: Single-Site Inspector, Rule Editor & Live Tester
    state.inspectState?.let { inspect ->
        SiteInspectSheet(
            inspectState = inspect,
            onDismiss = viewModel::dismissInspect,
            onKeywordChange = viewModel::updateInspectKeyword,
            onRunTest = viewModel::testSingleSite,
            onSaveRule = viewModel::saveEditedRule,
            onDeleteRule = { viewModel.deleteRule(inspect.site.id) },
            onVerifyCf = { url -> viewModel.openCfVerifyForSite(inspect.site, url) },
        )
    }

    // WebView CF Verify Dialog
    state.cfVerifySite?.let { (site, url) ->
        CloudflareVerifyDialog(
            siteName = site.name,
            targetUrl = url,
            onVerified = viewModel::onCfVerified,
            onDismiss = viewModel::dismissCfVerify,
        )
    }
}

@Composable
private fun NodeTelemetryRow(
    rule: SiteRule,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onVerifyCf: () -> Unit,
) {
    val c = InstrumentTheme.colors
    val barColor = when (rule.healthStatus) {
        SiteHealthStatus.OK -> c.ok
        SiteHealthStatus.CF_CHALLENGE -> c.warn
        else -> c.fail
    }
    val (symbol, codeColor) = when (rule.healthStatus) {
        SiteHealthStatus.OK -> "●" to c.ok
        SiteHealthStatus.CF_CHALLENGE -> "◆" to c.warn
        SiteHealthStatus.UNKNOWN -> "○" to c.muted
        else -> "×" to c.muted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = rule.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (rule.enabled) c.text else c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (rule.sourceType != SiteSourceType.HTML) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(c.chip)
                                .border(1.dp, c.hairline, RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = rule.sourceType.name,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = c.muted,
                            )
                        }
                    }
                }
                Text(
                    text = rule.host.ifBlank { rule.searchUrl },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val statusLine = buildString {
                    append("$symbol ${rule.healthStatus.label}")
                    if (rule.lastLatencyMs != null && rule.healthStatus != SiteHealthStatus.UNKNOWN) {
                        append(" · ${rule.lastLatencyMs}ms")
                    }
                }
                Text(
                    text = statusLine,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (rule.healthStatus == SiteHealthStatus.OK || rule.healthStatus == SiteHealthStatus.CF_CHALLENGE) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    color = codeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (rule.healthStatus == SiteHealthStatus.CF_CHALLENGE) {
                Surface(
                    onClick = onVerifyCf,
                    shape = RoundedCornerShape(5.dp),
                    color = c.warn.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, c.warn),
                ) {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "过盾",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = c.warn,
                        )
                    }
                }
            }
            InstrumentSwitch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteInspectSheet(
    inspectState: SiteInspectState,
    onDismiss: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onRunTest: () -> Unit,
    onSaveRule: (SiteRule) -> Unit,
    onDeleteRule: () -> Unit,
    onVerifyCf: (String?) -> Unit,
) {
    val c = InstrumentTheme.colors
    val site = inspectState.site
    var searchUrl by remember(site.id) { mutableStateOf(site.searchUrl) }
    var apiKey by remember(site.id) {
        mutableStateOf(
            site.headers.entries.firstOrNull { it.key.equals("X-Api-Key", ignoreCase = true) }?.value.orEmpty(),
        )
    }
    var groupExpr by remember(site.id) { mutableStateOf(site.groupExpr) }
    var titleExpr by remember(site.id) { mutableStateOf(site.titleExpr) }
    var magnetExpr by remember(site.id) { mutableStateOf(site.magnetExpr) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        contentColor = c.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${site.name} (#${site.id})",
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )
                TextButton(onClick = onDeleteRule) {
                    Text("删除规则", color = MaterialTheme.colorScheme.error)
                }
            }

            // Live Single-Site Test Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inspectState.testKeyword,
                    onValueChange = onKeywordChange,
                    label = { Text("调试关键词") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onRunTest,
                    enabled = !inspectState.isTesting,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accent,
                        contentColor = c.onAccent,
                    ),
                ) {
                    Text(if (inspectState.isTesting) "测试中" else "调试")
                }
            }

            inspectState.testSummary?.let { summary ->
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.ok,
                )
            }
            inspectState.challengeUrl?.let { url ->
                OutlinedButton(
                    onClick = { onVerifyCf(url) },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, c.warn),
                ) {
                    Text("打开 WebView 过盾后重测", color = c.warn)
                }
            }

            if (inspectState.previewResults.isNotEmpty()) {
                Text(
                    text = "解析预览（前 ${inspectState.previewResults.size} 条）",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                )
                inspectState.previewResults.forEach { item ->
                    val seedLabel = item.seeders?.let { " · ↑$it" }.orEmpty()
                    Text(
                        text = "• ${item.title} [${item.sizeText.ifBlank { "未知大小" }}$seedLabel]",
                        fontSize = 12.sp,
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            InstrumentHairline()

            OutlinedTextField(
                value = searchUrl,
                onValueChange = { searchUrl = it },
                label = { Text("searchUrl") },
                modifier = Modifier.fillMaxWidth(),
            )
            when (site.sourceType) {
                SiteSourceType.PROWLARR, SiteSourceType.TORZNAB -> {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("X-Api-Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SiteSourceType.RSS -> {
                    Text(
                        text = "RSS / Atom 订阅源自动解析标题、大小、做种数与磁力链，仅需配置 searchUrl。",
                        fontSize = 12.sp,
                        color = c.muted,
                    )
                }
                SiteSourceType.JSON_API -> {
                    OutlinedTextField(
                        value = groupExpr,
                        onValueChange = { groupExpr = it },
                        label = { Text("group JSON 路径（留空为根数组）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = titleExpr,
                        onValueChange = { titleExpr = it },
                        label = { Text("title 字段路径（如 name）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = magnetExpr,
                        onValueChange = { magnetExpr = it },
                        label = { Text("magnet / hash 字段（如 magnet_uri|info_hash）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SiteSourceType.HTML -> {
                    OutlinedTextField(
                        value = groupExpr,
                        onValueChange = { groupExpr = it },
                        label = { Text("group (XPath 或 css:)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = titleExpr,
                        onValueChange = { titleExpr = it },
                        label = { Text("title (XPath 或 css:)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = magnetExpr,
                        onValueChange = { magnetExpr = it },
                        label = { Text("magnet (XPath 或 css:)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(
                onClick = {
                    val updatedHeaders = site.headers.toMutableMap().apply {
                        keys.filter { it.equals("X-Api-Key", ignoreCase = true) }.forEach { remove(it) }
                        if (apiKey.isNotBlank()) put("X-Api-Key", apiKey.trim())
                    }
                    onSaveRule(
                        site.copy(
                            searchUrl = searchUrl.trim(),
                            headers = updatedHeaders,
                            groupExpr = groupExpr.trim(),
                            titleExpr = titleExpr.trim(),
                            magnetExpr = magnetExpr.trim(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accent,
                    contentColor = c.onAccent,
                ),
            ) {
                Text("保存修改", fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AddApiSourceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: SiteSourceType, serverUrl: String, apiKey: String, indexerId: String) -> Unit,
) {
    var selectedType by remember { mutableStateOf(SiteSourceType.PROWLARR) }
    var name by remember { mutableStateOf("Prowlarr 聚合") }
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var indexerId by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 Prowlarr / Torznab 源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SiteSourceType.PROWLARR, SiteSourceType.TORZNAB).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                if (name == "Prowlarr 聚合" || name == "Torznab 聚合") {
                                    name = if (type == SiteSourceType.PROWLARR) "Prowlarr 聚合" else "Torznab 聚合"
                                }
                            },
                            label = { Text(type.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://100.x.y.z:9696 或 https://域名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (X-Api-Key)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (selectedType == SiteSourceType.TORZNAB) {
                    OutlinedTextField(
                        value = indexerId,
                        onValueChange = { indexerId = it },
                        label = { Text("索引器 ID（留空则直接拼接 /api）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = if (selectedType == SiteSourceType.PROWLARR) {
                        "将自动拼接 /api/v1/search 并附带 X-Api-Key 请求头。"
                    } else {
                        "兼容 Prowlarr 单索引器、Jackett 及 Bitmagnet Torznab 接口。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedType, serverUrl, apiKey, indexerId) },
                enabled = serverUrl.isNotBlank(),
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

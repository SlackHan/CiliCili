package com.lanhan.cilicili.ui.sites

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSourceType
import com.lanhan.cilicili.ui.components.CiliIcons
import com.lanhan.cilicili.ui.components.CloudflareVerifyDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(
    viewModel: SitesViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    Column(modifier = modifier.fillMaxSize()) {
        // Top Summary & Action Bar
        val enabledCount = state.allRules.count { it.enabled }
        val healthyCount = state.allRules.count { it.healthStatus == SiteHealthStatus.OK }
        val cfCount = state.allRules.count { it.healthStatus == SiteHealthStatus.CF_CHALLENGE }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "磁力站点 (${state.allRules.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "已启用 $enabledCount · 可用 $healthyCount · 需过盾 $cfCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showAddApiDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加聚合源")
            }
            Button(
                onClick = {
                    if (state.isCheckingHealth) viewModel.cancelHealthCheck() else showHealthDialog = true
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(CiliIcons.SpeedTest, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.isCheckingHealth) "停止" else "一键测活")
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
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
                        text = { Text("重置为内置 39 条规则") },
                        onClick = {
                            showMoreMenu = false
                            viewModel.resetDefaults()
                        },
                    )
                }
            }
        }

        // Health Check Progress Banner
        state.healthProgress?.takeIf { state.isCheckingHealth }?.let { prog ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                LinearProgressIndicator(
                    progress = {
                        if (prog.total > 0) prog.completed.toFloat() / prog.total else 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "正在测活 ${prog.completed}/${prog.total} · ${prog.lastCheckedSite}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Quick Search & Filter Chips
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("筛选站点名或域名...") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "清除")
                    }
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SiteFilterTab.entries.forEach { tab ->
                FilterChip(
                    selected = state.activeFilter == tab,
                    onClick = { viewModel.setFilter(tab) },
                    label = { Text(tab.label) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.filteredRules, key = { it.id }) { rule ->
                SiteRuleCard(
                    rule = rule,
                    onToggle = { enabled -> viewModel.toggleSite(rule.id, enabled) },
                    onClick = { viewModel.openInspect(rule) },
                    onVerifyCf = { viewModel.openCfVerifyForSite(rule) },
                )
            }
        }
    }

    // Dialog: Start Health Check with custom probe keyword
    if (showHealthDialog) {
        var keyword by remember { mutableStateOf("ubuntu") }
        AlertDialog(
            onDismissRequest = { showHealthDialog = false },
            title = { Text("一键批量测活") },
            text = {
                Column {
                    Text(
                        text = "将并发探测全部 ${state.allRules.size} 个站点，检测域名连通性、反爬状态及 XPath 解析是否有效。",
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
                        Text("覆盖现有全部规则（不勾选则按 ID 合并）", style = MaterialTheme.typography.bodySmall)
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
                        label = { Text("JSON Raw URL (如 GitHub Gist)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { replaceAll = !replaceAll },
                    ) {
                        Checkbox(checked = replaceAll, onCheckedChange = { replaceAll = it })
                        Text("覆盖现有全部规则", style = MaterialTheme.typography.bodySmall)
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
private fun SiteRuleCard(
    rule: SiteRule,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onVerifyCf: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (rule.sourceType != SiteSourceType.HTML) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = rule.sourceType.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    HealthBadge(status = rule.healthStatus, latencyMs = rule.lastLatencyMs)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = rule.host.ifBlank { rule.searchUrl },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!rule.lastErrorMessage.isNullOrBlank()) {
                    Text(
                        text = rule.lastErrorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rule.healthStatus == SiteHealthStatus.CF_CHALLENGE) {
                    TextButton(onClick = onVerifyCf) {
                        Text("过盾")
                    }
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                )
            }
        }
    }
}

@Composable
private fun HealthBadge(status: SiteHealthStatus, latencyMs: Long?) {
    val (bg, fg) = when (status) {
        SiteHealthStatus.OK ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SiteHealthStatus.CF_CHALLENGE ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        SiteHealthStatus.HTTP_ERROR,
        SiteHealthStatus.NETWORK_ERROR,
        SiteHealthStatus.PARSE_ERROR ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = buildString {
        append(status.label)
        if (latencyMs != null && status != SiteHealthStatus.UNKNOWN) {
            append(" ${latencyMs}ms")
        }
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                Button(onClick = onRunTest, enabled = !inspectState.isTesting) {
                    Text(if (inspectState.isTesting) "测试中" else "单站调试")
                }
            }

            inspectState.testSummary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            inspectState.challengeUrl?.let { url ->
                OutlinedButton(onClick = { onVerifyCf(url) }) {
                    Icon(CiliIcons.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("打开 WebView 过盾后重测")
                }
            }

            if (inspectState.previewResults.isNotEmpty()) {
                Text("解析预览（前 ${inspectState.previewResults.size} 条）：", style = MaterialTheme.typography.labelMedium)
                inspectState.previewResults.forEach { item ->
                    val seedLabel = item.seeders?.let { " · 做种 $it" }.orEmpty()
                    Text(
                        text = "• ${item.title} [${item.sizeText.ifBlank { "未知大小" }}$seedLabel] ${if (item.magnetUri != null) "(直出磁力)" else "(详情页)"}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = searchUrl,
                onValueChange = { searchUrl = it },
                label = { Text("searchUrl") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (site.sourceType != SiteSourceType.HTML) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("X-Api-Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = groupExpr,
                    onValueChange = { groupExpr = it },
                    label = { Text("group XPath") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = titleExpr,
                    onValueChange = { titleExpr = it },
                    label = { Text("title XPath") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = magnetExpr,
                    onValueChange = { magnetExpr = it },
                    label = { Text("magnet XPath") },
                    modifier = Modifier.fillMaxWidth(),
                )
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
            ) {
                Text("保存修改")
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
                        "将自动拼接 /api/v1/search 并附带 X-Api-Key 请求头，一次搜索汇总 Prowlarr 内全部启用站点。"
                    } else {
                        "兼容 Prowlarr 单索引器、Jackett 及 Bitmagnet Torznab XML 接口。"
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

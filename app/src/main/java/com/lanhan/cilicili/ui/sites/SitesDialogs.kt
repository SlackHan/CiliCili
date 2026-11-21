package com.lanhan.cilicili.ui.sites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanhan.cilicili.data.local.SettingsRepository
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSourceType
import com.lanhan.cilicili.ui.components.InstrumentHairline
import com.lanhan.cilicili.ui.theme.InstrumentTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BatchHealthCheckDialog(
    totalCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (keyword: String) -> Unit,
) {
    val c = InstrumentTheme.colors
    var keyword by remember { mutableStateOf("ubuntu") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                onClick = { onConfirm(keyword) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accent,
                    contentColor = c.onAccent,
                ),
            ) {
                Text("开始测活")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
internal fun ImportJsonRulesDialog(
    onDismiss: () -> Unit,
    onConfirm: (rawText: String, replaceAll: Boolean) -> Unit,
) {
    var rawText by remember { mutableStateOf("") }
    var replaceAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                onClick = { onConfirm(rawText, replaceAll) },
                enabled = rawText.isNotBlank(),
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
internal fun RemoteSubscriptionDialog(
    subscriptionUrl: String,
    autoSyncSubscription: Boolean,
    lastSubscriptionSyncAt: Long,
    lastSubscriptionSyncSummary: String,
    onAutoSyncChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (url: String, replaceAll: Boolean) -> Unit,
) {
    val c = InstrumentTheme.colors
    var url by remember(subscriptionUrl) {
        mutableStateOf(subscriptionUrl.ifBlank { SettingsRepository.DEFAULT_SUBSCRIPTION_URL })
    }
    var replaceAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                Spacer(modifier = Modifier.height(8.dp))

                // Official Daily Live-Checked Subscription Preset Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SettingsRepository.OFFICIAL_SUBSCRIPTION_PRESETS.forEach { (label, presetUrl) ->
                        val isSelected = url.trim() == presetUrl
                        Surface(
                            onClick = { url = presetUrl },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) c.activeBg else c.chip,
                            border = BorderStroke(1.dp, if (isSelected) c.accent else c.hairline),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) c.accent else c.muted),
                                )
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) c.activeText else c.text,
                                )
                            }
                        }
                    }
                }

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
                        onAutoSyncChange(!autoSyncSubscription)
                    },
                ) {
                    Checkbox(
                        checked = autoSyncSubscription,
                        onCheckedChange = onAutoSyncChange,
                    )
                    Text("启动时自动同步（每 24 小时）", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "增量同步会自动停用已从订阅源剔除的失效公开站点，完整保留自建规则与 Prowlarr / Torznab 源。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
                if (lastSubscriptionSyncSummary.isNotBlank()) {
                    val timePrefix = if (lastSubscriptionSyncAt > 0L) {
                        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        "${fmt.format(Date(lastSubscriptionSyncAt))} · "
                    } else {
                        ""
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "上次状态：$timePrefix$lastSubscriptionSyncSummary",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(url, replaceAll) },
                enabled = url.isNotBlank(),
            ) {
                Text("立即拉取")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SiteInspectSheet(
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
internal fun AddApiSourceDialog(
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

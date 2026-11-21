package com.lanhan.cilicili.ui.sites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSourceType
import com.lanhan.cilicili.ui.components.InstrumentSwitch
import com.lanhan.cilicili.ui.theme.InstrumentTheme

@Composable
internal fun SitesTelemetryHeader(
    state: SitesUiState,
    totalCount: Int,
    enabledCount: Int,
    healthyCount: Int,
    cfCount: Int,
    otherCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (SiteFilterTab) -> Unit,
    onShowAddApiDialog: () -> Unit,
    onEnableOnlyHealthy: () -> Unit,
    onEnableAll: () -> Unit,
    onShowImportJsonDialog: () -> Unit,
    onShowSubscriptionDialog: () -> Unit,
    onExportJson: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    val c = InstrumentTheme.colors
    var showMoreMenu by remember { mutableStateOf(false) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(c.ok))
                    Text("$healthyCount", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.text)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).rotate(45f).background(c.warn))
                    Text("$cfCount", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.text)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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
                        onValueChange = onSearchQueryChange,
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
                                .clickable { onSearchQueryChange("") },
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
                            onShowAddApiDialog()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("仅启用可用站点") },
                        onClick = {
                            showMoreMenu = false
                            onEnableOnlyHealthy()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("全部启用") },
                        onClick = {
                            showMoreMenu = false
                            onEnableAll()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("导入 JSON 规则") },
                        onClick = {
                            showMoreMenu = false
                            onShowImportJsonDialog()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("远程订阅更新") },
                        onClick = {
                            showMoreMenu = false
                            onShowSubscriptionDialog()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出 JSON 到剪贴板") },
                        onClick = {
                            showMoreMenu = false
                            onExportJson()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("重置为内置规则") },
                        onClick = {
                            showMoreMenu = false
                            onResetDefaults()
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
                    onClick = { onFilterSelect(tab) },
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
}

@Composable
internal fun NodeTelemetryRow(
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

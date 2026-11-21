package com.lanhan.cilicili.ui.sites

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.ui.components.CloudflareVerifyDialog
import com.lanhan.cilicili.ui.components.InstrumentHairline
import com.lanhan.cilicili.ui.components.InstrumentTopBar
import com.lanhan.cilicili.ui.theme.InstrumentTheme

@Composable
fun SitesScreen(
    viewModel: SitesViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = InstrumentTheme.colors

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
        SitesTelemetryHeader(
            state = state,
            totalCount = totalCount,
            enabledCount = enabledCount,
            healthyCount = healthyCount,
            cfCount = cfCount,
            otherCount = otherCount,
            onSearchQueryChange = viewModel::setSearchQuery,
            onFilterSelect = viewModel::setFilter,
            onShowAddApiDialog = { showAddApiDialog = true },
            onEnableOnlyHealthy = viewModel::enableOnlyHealthy,
            onEnableAll = viewModel::enableAll,
            onShowImportJsonDialog = { showImportJsonDialog = true },
            onShowSubscriptionDialog = { showSubscriptionDialog = true },
            onExportJson = viewModel::exportJson,
            onResetDefaults = viewModel::resetDefaults,
        )

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

    if (showHealthDialog) {
        BatchHealthCheckDialog(
            totalCount = totalCount,
            onDismiss = { showHealthDialog = false },
            onConfirm = { keyword ->
                showHealthDialog = false
                viewModel.runHealthCheck(keyword)
            },
        )
    }

    if (showImportJsonDialog) {
        ImportJsonRulesDialog(
            onDismiss = { showImportJsonDialog = false },
            onConfirm = { rawText, replaceAll ->
                showImportJsonDialog = false
                viewModel.importJson(rawText, replaceAll)
            },
        )
    }

    if (showSubscriptionDialog) {
        RemoteSubscriptionDialog(
            subscriptionUrl = state.subscriptionUrl,
            autoSyncSubscription = state.autoSyncSubscription,
            lastSubscriptionSyncAt = state.lastSubscriptionSyncAt,
            lastSubscriptionSyncSummary = state.lastSubscriptionSyncSummary,
            onAutoSyncChange = viewModel::setAutoSyncSubscription,
            onDismiss = { showSubscriptionDialog = false },
            onConfirm = { url, replaceAll ->
                showSubscriptionDialog = false
                viewModel.importFromUrl(url, replaceAll)
            },
        )
    }

    if (showAddApiDialog) {
        AddApiSourceDialog(
            onDismiss = { showAddApiDialog = false },
            onConfirm = { name, type, url, apiKey, indexerId ->
                showAddApiDialog = false
                viewModel.addApiSource(name, type, url, apiKey, indexerId)
            },
        )
    }

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

    state.cfVerifySite?.let { (site, url) ->
        CloudflareVerifyDialog(
            siteName = site.name,
            targetUrl = url,
            onVerified = viewModel::onCfVerified,
            onDismiss = viewModel::dismissCfVerify,
        )
    }
}

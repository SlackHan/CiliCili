package com.lanhan.cilicili.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanhan.cilicili.domain.model.AggregatedMagnetItem
import com.lanhan.cilicili.domain.model.DohProvider
import com.lanhan.cilicili.domain.model.MinSizeFilter
import com.lanhan.cilicili.domain.model.SiteExecutionState
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.domain.model.SiteSearchStatus
import com.lanhan.cilicili.domain.model.SortOption
import com.lanhan.cilicili.ui.components.CiliIcons
import com.lanhan.cilicili.ui.components.CloudflareVerifyDialog
import com.lanhan.cilicili.ui.components.InstrumentHairline
import com.lanhan.cilicili.ui.components.InstrumentTopBar
import com.lanhan.cilicili.ui.theme.InstrumentTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val c = InstrumentTheme.colors

    var showSiteStatusSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSizeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.CopyToClipboard -> {
                    copyText(context, effect.text)
                    snackbarHostState.showSnackbar(effect.label)
                }
                is SearchEffect.OpenMagnet -> {
                    val opened = launchMagnetIntent(context, effect.magnetUri)
                    if (!opened) {
                        copyText(context, effect.magnetUri)
                        snackbarHostState.showSnackbar("未检测到磁力下载器，已自动复制磁力链接")
                    }
                }
                is SearchEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    val isHome = state.activeKeyword.isEmpty()

    BackHandler(enabled = !isHome) {
        focusManager.clearFocus()
        viewModel.clearSearch()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        // Top Telemetry Strip
        InstrumentTopBar(
            leading = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c.accent),
                    )
                    Text(
                        text = "CILICILI",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = c.text,
                    )
                    StealthModeBadge(visible = !isHome && state.incognitoMode)
                }
            },
            trailing = {
                val nodeSummary = if (state.healthySiteCount > 0) {
                    "可用 ${state.healthySiteCount}/${state.totalSiteCount}"
                } else {
                    "启用 ${state.enabledSiteCount}/${state.totalSiteCount}"
                }
                val dnsLabel = when (state.dohProvider) {
                    DohProvider.SYSTEM -> "DNS: SYS"
                    DohProvider.ALIDNS -> "DoH: ALI"
                    DohProvider.CLOUDFLARE -> "DoH: CF"
                    DohProvider.QUAD9 -> "DoH: Q9"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (state.healthySiteCount > 0) c.ok else c.muted),
                    )
                    Text(
                        text = "$nodeSummary · $dnsLabel",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.muted,
                    )
                }
            },
        )

        if (isHome) {
            SearchHomeBody(
                state = state,
                showSortMenu = showSortMenu,
                onToggleSortMenu = { showSortMenu = it },
                onQueryChange = viewModel::onQueryChange,
                onSubmitSearch = {
                    focusManager.clearFocus()
                    viewModel.submitSearch()
                },
                onToggleIncognito = viewModel::toggleIncognito,
                onSelectSort = viewModel::setSortOption,
                onClickHistory = { kw ->
                    focusManager.clearFocus()
                    viewModel.submitSearch(kw)
                },
                onDeleteHistory = viewModel::deleteHistoryItem,
                onClearHistory = viewModel::clearHistory,
            )
        } else {
            SearchResultsBody(
                state = state,
                showSortMenu = showSortMenu,
                onToggleSortMenu = { showSortMenu = it },
                showSizeMenu = showSizeMenu,
                onToggleSizeMenu = { showSizeMenu = it },
                onQueryChange = viewModel::onQueryChange,
                onSubmitSearch = {
                    focusManager.clearFocus()
                    viewModel.submitSearch()
                },
                onCancelSearch = viewModel::cancelSearch,
                onBackHome = {
                    focusManager.clearFocus()
                    viewModel.clearSearch()
                },
                onSelectSort = viewModel::setSortOption,
                onSelectMinSize = viewModel::setMinSizeFilter,
                onOpenSiteStates = { showSiteStatusSheet = true },
                onResultClicked = viewModel::onResultClicked,
                onCopyClick = { viewModel.executeMagnetAction(it, MagnetIntentAction.COPY) },
                onDownloadClick = { viewModel.executeMagnetAction(it, MagnetIntentAction.OPEN_DOWNLOADER) },
                onLoadNextPage = viewModel::loadNextPage,
            )
        }
    }

    // Bottom Sheet: Result Action Chooser
    state.sheetItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSheet,
            containerColor = c.panel,
            contentColor = c.text,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )
                Spacer(modifier = Modifier.height(6.dp))
                val meta = buildList {
                    if (item.sizeText.isNotBlank()) add(item.sizeText)
                    item.seeders?.let { add("做种 $it") }
                    if (item.dateText.isNotBlank()) add(item.dateText)
                    add(item.sources.joinToString(", ") { it.siteName })
                }.joinToString(" · ")
                Text(
                    text = meta,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                )
                if (!item.infoHash.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "#${item.infoHash}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.executeMagnetAction(item, MagnetIntentAction.COPY) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        Text("复制磁力", color = c.text, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { viewModel.executeMagnetAction(item, MagnetIntentAction.OPEN_DOWNLOADER) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accent,
                            contentColor = c.onAccent,
                        ),
                    ) {
                        Text("唤起下载器", fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Bottom Sheet: Per-site execution statuses + one-tap CF challenge trigger
    if (showSiteStatusSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSiteStatusSheet = false },
            containerColor = c.panel,
            contentColor = c.text,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "各站点本次响应 (${state.siteStates.size})",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )
                Spacer(modifier = Modifier.height(10.dp))
                InstrumentHairline()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                ) {
                    items(state.siteStates, key = { it.site.id }) { siteState ->
                        SiteExecutionRow(
                            siteState = siteState,
                            onVerifyCf = { challengeUrl ->
                                showSiteStatusSheet = false
                                viewModel.openCfChallengeForSite(siteState.site, challengeUrl)
                            },
                        )
                        InstrumentHairline()
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // On-Demand Sandboxed WebView CF Verification Dialog
    state.cfChallengeTarget?.let { target ->
        CloudflareVerifyDialog(
            siteName = target.siteName,
            targetUrl = target.challengeUrl,
            onVerified = viewModel::onCfVerified,
            onDismiss = viewModel::dismissCfChallenge,
        )
    }
}

@Composable
private fun StealthModeBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = InstrumentTheme.colors
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
        exit = fadeOut(tween(180)) + shrinkHorizontally(tween(180)),
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = c.stealthChipBg,
            border = BorderStroke(1.dp, c.stealthBorder),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = CiliIcons.Incognito,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = c.stealthInk,
                )
                Text(
                    text = "无痕",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = c.stealthInk,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CommandPromptGlyph(
    incognitoMode: Boolean,
    boxWidth: Dp,
    fontSize: TextUnit,
    iconSize: Dp,
    label: String,
) {
    val c = InstrumentTheme.colors
    Box(
        modifier = Modifier.width(boxWidth),
        contentAlignment = Alignment.CenterStart,
    ) {
        Crossfade(
            targetState = incognitoMode,
            animationSpec = tween(200),
            label = label,
        ) { stealth ->
            if (stealth) {
                Icon(
                    imageVector = CiliIcons.Incognito,
                    contentDescription = "无痕模式",
                    modifier = Modifier.size(iconSize),
                    tint = c.stealthInk,
                )
            } else {
                Text(
                    text = ">_",
                    fontSize = fontSize,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = c.accent,
                )
            }
        }
    }
}

@Composable
private fun SearchHomeBody(
    state: SearchUiState,
    showSortMenu: Boolean,
    onToggleSortMenu: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onToggleIncognito: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onClickHistory: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val c = InstrumentTheme.colors
    val haptic = LocalHapticFeedback.current
    val stealth = state.incognitoMode

    val heroWashColor by animateColorAsState(
        targetValue = if (stealth) c.stealthBg.copy(alpha = 0.65f) else Color.Transparent,
        animationSpec = tween(260),
        label = "heroWash",
    )
    val dotGridColor by animateColorAsState(
        targetValue = if (stealth) c.stealthInk.copy(alpha = 0.16f) else c.dotGrid,
        animationSpec = tween(260),
        label = "dotGrid",
    )
    val cardBg by animateColorAsState(
        targetValue = if (stealth) c.stealthSurface else c.panel,
        animationSpec = tween(240),
        label = "cardBg",
    )
    val cardBorder by animateColorAsState(
        targetValue = if (stealth) c.stealthBorder else c.hairline,
        animationSpec = tween(240),
        label = "cardBorder",
    )
    val railAlpha by animateFloatAsState(
        targetValue = if (stealth) 0.85f else 0f,
        animationSpec = tween(240),
        label = "railAlpha",
    )
    val chipBg by animateColorAsState(
        targetValue = if (stealth) c.stealthChipBg else c.chip,
        animationSpec = tween(220),
        label = "chipBg",
    )
    val chipBorder by animateColorAsState(
        targetValue = if (stealth) c.stealthBorder else c.hairline,
        animationSpec = tween(220),
        label = "chipBorder",
    )
    val chipText by animateColorAsState(
        targetValue = if (stealth) c.stealthInk else c.text,
        animationSpec = tween(220),
        label = "chipText",
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (stealth) 0.22f else 0f,
        animationSpec = tween(220),
        label = "haloAlpha",
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Dot-Matrix Hero Header + Structured Command Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroWashColor),
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val step = 14.dp.toPx()
                val radius = 1.dp.toPx()
                var x = step / 2f
                while (x < size.width) {
                    var y = step / 2f
                    while (y < size.height) {
                        drawCircle(color = dotGridColor, radius = radius, center = Offset(x, y))
                        y += step
                    }
                    x += step
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "多源磁力检索",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        StealthModeBadge(visible = stealth)
                    }
                    NodeSignalBars(bars = state.signalBars)
                }

                // Structured Command Input Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                drawContent()
                                if (railAlpha > 0f) {
                                    drawRect(
                                        color = c.stealthInk.copy(alpha = railAlpha),
                                        topLeft = Offset.Zero,
                                        size = Size(size.width, 2.dp.toPx()),
                                    )
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CommandPromptGlyph(
                                incognitoMode = stealth,
                                boxWidth = 18.dp,
                                fontSize = 13.sp,
                                iconSize = 15.dp,
                                label = "homePrompt",
                            )
                            BasicTextField(
                                value = state.query,
                                onValueChange = onQueryChange,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    color = c.text,
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = SolidColor(c.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (state.query.isEmpty()) {
                                            Crossfade(
                                                targetState = stealth,
                                                animationSpec = tween(200),
                                                label = "homePlaceholder",
                                            ) { isStealth ->
                                                Text(
                                                    text = if (isStealth) {
                                                        "无痕检索 · 关键词不写入本地历史..."
                                                    } else {
                                                        "输入关键词、番号或 InfoHash..."
                                                    },
                                                    fontSize = 14.sp,
                                                    color = if (isStealth) {
                                                        c.stealthInk.copy(alpha = 0.68f)
                                                    } else {
                                                        c.muted
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        inner()
                                    }
                                },
                            )
                            if (state.query.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onQueryChange("") },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "×",
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = c.muted,
                                    )
                                }
                            }
                        }

                        InstrumentHairline()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Incognito toggle
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleIncognito()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier.size(10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (stealth) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(c.stealthInk.copy(alpha = haloAlpha)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(c.stealthInk),
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .border(1.2.dp, c.muted, CircleShape),
                                                )
                                            }
                                        }
                                        Text(
                                            text = "无痕",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = chipText,
                                        )
                                    }
                                }

                                // Sort preset selector
                                Box {
                                    Surface(
                                        onClick = { onToggleSortMenu(true) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = c.chip,
                                        border = BorderStroke(1.dp, c.hairline),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .height(32.dp)
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "排序: ${state.sortOption.shortLabel()}",
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = c.text,
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { onToggleSortMenu(false) },
                                    ) {
                                        SortOption.entries.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.label) },
                                                onClick = {
                                                    onSelectSort(option)
                                                    onToggleSortMenu(false)
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            // Primary CTA
                            Surface(
                                onClick = onSubmitSearch,
                                shape = RoundedCornerShape(6.dp),
                                color = c.accent,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(32.dp)
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "检索 ↵",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.onAccent,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        InstrumentHairline()

        // Recent Search History Table
        if (state.history.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "近期检索",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c.muted,
                        )
                        AnimatedVisibility(
                            visible = stealth,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(180)),
                        ) {
                            Text(
                                text = "· 已暂停记录",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = c.stealthInk.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Text(
                        text = "清空",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = c.panel,
                    border = BorderStroke(1.dp, c.hairline),
                ) {
                    LazyColumn {
                        itemsIndexed(state.history, key = { _, kw -> kw }) { idx, kw ->
                            if (idx > 0) InstrumentHairline()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onClickHistory(kw) }
                                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = (idx + 1).toString().padStart(2, '0'),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.muted,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = kw,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onDeleteHistory(kw) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "×",
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = c.muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeSignalBars(bars: List<SiteSignalBar>) {
    val c = InstrumentTheme.colors
    if (bars.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(24.dp),
    ) {
        bars.forEach { bar ->
            val (heightDp, color) = when (bar.status) {
                SiteHealthStatus.OK -> {
                    val ms = bar.latencyMs ?: 400L
                    val h = when {
                        ms < 300L -> 22.dp
                        ms < 800L -> 16.dp
                        else -> 12.dp
                    }
                    h to c.ok
                }
                SiteHealthStatus.CF_CHALLENGE -> 10.dp to c.warn
                else -> 6.dp to c.fail
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(heightDp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun SearchResultsBody(
    state: SearchUiState,
    showSortMenu: Boolean,
    onToggleSortMenu: (Boolean) -> Unit,
    showSizeMenu: Boolean,
    onToggleSizeMenu: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onCancelSearch: () -> Unit,
    onBackHome: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onSelectMinSize: (MinSizeFilter) -> Unit,
    onOpenSiteStates: () -> Unit,
    onResultClicked: (AggregatedMagnetItem) -> Unit,
    onCopyClick: (AggregatedMagnetItem) -> Unit,
    onDownloadClick: (AggregatedMagnetItem) -> Unit,
    onLoadNextPage: () -> Unit,
) {
    val c = InstrumentTheme.colors
    val stealth = state.incognitoMode
    val resultsBarBg by animateColorAsState(
        targetValue = if (stealth) c.stealthSurface else c.panel,
        animationSpec = tween(240),
        label = "resultsBarBg",
    )
    val resultsBarBorder by animateColorAsState(
        targetValue = if (stealth) c.stealthBorder else c.hairline,
        animationSpec = tween(240),
        label = "resultsBarBorder",
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Editable Command Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = onBackHome,
                shape = RoundedCornerShape(6.dp),
                color = c.chip,
                border = BorderStroke(1.dp, c.hairline),
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "←",
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.text,
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                color = resultsBarBg,
                border = BorderStroke(1.dp, resultsBarBorder),
            ) {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CommandPromptGlyph(
                        incognitoMode = stealth,
                        boxWidth = 16.dp,
                        fontSize = 12.sp,
                        iconSize = 14.dp,
                        label = "resultsPrompt",
                    )
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = c.text,
                        ),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    )
                }
            }

            Surface(
                onClick = { if (state.isSearching) onCancelSearch() else onSubmitSearch() },
                shape = RoundedCornerShape(6.dp),
                color = if (state.isSearching) c.activeBg else c.accent,
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.isSearching) "停止" else "重搜",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.isSearching) c.activeText else c.onAccent,
                    )
                }
            }
        }

        if (state.isSearching || state.isLoadingMore) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = c.accent,
                trackColor = c.track,
            )
        } else {
            InstrumentHairline()
        }

        // Telemetry + Sort + Size Filter Strip
        val successCount = state.siteStates.count { it.status is SiteSearchStatus.Success }
        val cfCount = state.siteStates.count { it.status is SiteSearchStatus.ChallengeRequired }
        val runningCount = state.siteStates.count {
            it.status is SiteSearchStatus.Running || it.status is SiteSearchStatus.Pending
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onOpenSiteStates,
                shape = RoundedCornerShape(6.dp),
                color = c.chip,
                border = BorderStroke(1.dp, c.hairline),
            ) {
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "去重 ${state.displayedResults.size}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.text,
                    )
                    Text("·", fontSize = 12.sp, color = c.muted)
                    Text(
                        text = "OK $successCount",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = c.ok,
                    )
                    if (cfCount > 0) {
                        Text(
                            text = "· 盾 $cfCount",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = c.warn,
                        )
                    }
                    if (runningCount > 0) {
                        Text(
                            text = "· 进行 $runningCount",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c.muted,
                        )
                    }
                    Text("▾", fontSize = 12.sp, color = c.muted)
                }
            }

            Box {
                val active = state.sortOption != SortOption.RELEVANCE
                Surface(
                    onClick = { onToggleSortMenu(true) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) c.activeBg else c.chip,
                    border = BorderStroke(1.dp, if (active) c.activeBg else c.hairline),
                ) {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${state.sortOption.shortLabel()} ▾",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (active) c.activeText else c.text,
                        )
                    }
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onToggleSortMenu(false) },
                ) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSelectSort(option)
                                onToggleSortMenu(false)
                            },
                        )
                    }
                }
            }

            Box {
                val active = state.minSizeFilter != MinSizeFilter.ALL
                Surface(
                    onClick = { onToggleSizeMenu(true) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) c.activeBg else c.chip,
                    border = BorderStroke(1.dp, if (active) c.activeBg else c.hairline),
                ) {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${state.minSizeFilter.label} ▾",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (active) c.activeText else c.text,
                        )
                    }
                }
                DropdownMenu(
                    expanded = showSizeMenu,
                    onDismissRequest = { onToggleSizeMenu(false) },
                ) {
                    MinSizeFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.label) },
                            onClick = {
                                onSelectMinSize(filter)
                                onToggleSizeMenu(false)
                            },
                        )
                    }
                }
            }
        }

        InstrumentHairline()

        // Results List or Empty State
        if (state.displayedResults.isEmpty() && !state.isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (state.totalAggregatedCount > 0) {
                            "结果已被当前体积条件过滤"
                        } else {
                            "未找到「${state.activeKeyword}」相关资源"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.text,
                    )
                    Text(
                        text = "可点击上方状态胶囊查看站点响应，或在「节点」页测活。",
                        fontSize = 12.sp,
                        color = c.muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.minSizeFilter != MinSizeFilter.ALL) {
                            OutlinedButton(
                                onClick = { onSelectMinSize(MinSizeFilter.ALL) },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, c.hairline),
                            ) {
                                Text("重置体积", color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenSiteStates,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, c.hairline),
                        ) {
                            Text("查看各站响应", color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(
                    items = state.displayedResults,
                    key = { it.dedupKey },
                ) { item ->
                    MagnetResultRow(
                        item = item,
                        isResolving = state.resolvingDedupKey == item.dedupKey,
                        onRowClick = { onResultClicked(item) },
                        onCopyClick = { onCopyClick(item) },
                        onDownloadClick = { onDownloadClick(item) },
                    )
                    InstrumentHairline()
                }
                item(key = "load_more_footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Surface(
                            onClick = onLoadNextPage,
                            enabled = !state.isSearching && !state.isLoadingMore,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            color = c.chip,
                            border = BorderStroke(1.dp, c.hairline),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (state.isLoadingMore) {
                                        "正在加载第 ${state.currentPage} 页..."
                                    } else {
                                        "加载第 ${(state.currentPage + 1).toString().padStart(2, '0')} 页 ↓"
                                    },
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.text,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MagnetResultRow(
    item: AggregatedMagnetItem,
    isResolving: Boolean,
    onRowClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    val c = InstrumentTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isResolving, onClick = onRowClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item.title,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val sourceLabel = if (item.sources.size > 1) {
                "${item.primarySource.siteName} +${item.sources.size - 1}"
            } else {
                item.primarySource.siteName
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.chip)
                    .border(1.dp, c.hairline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = sourceLabel,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )
            }

            if (item.sizeText.isNotBlank()) {
                Text(
                    text = item.sizeText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.text,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            item.seeders?.let { seeders ->
                Text(
                    text = "↑ $seeders",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (seeders > 0) c.ok else c.muted,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            if (item.dateText.isNotBlank()) {
                Text(
                    text = item.dateText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.infoHash?.take(8)?.uppercase()?.let { "#$it" }.orEmpty(),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = c.muted,
            )

            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = c.accent,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onCopyClick,
                        shape = RoundedCornerShape(5.dp),
                        color = c.chip,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "复制",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = c.text,
                            )
                        }
                    }
                    Surface(
                        onClick = onDownloadClick,
                        shape = RoundedCornerShape(5.dp),
                        color = c.chip,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "下载 ↗",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = c.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteExecutionRow(
    siteState: SiteExecutionState,
    onVerifyCf: (String) -> Unit,
) {
    val c = InstrumentTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = siteState.site.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
            )
            val (statusText, statusColor) = when (val st = siteState.status) {
                SiteSearchStatus.Pending -> "排队中..." to c.muted
                SiteSearchStatus.Running -> "请求中..." to c.muted
                is SiteSearchStatus.Success -> "● 命中 ${st.count} 条 · ${st.latencyMs}ms" to c.ok
                is SiteSearchStatus.ChallengeRequired -> "◆ 需过盾 · ${st.latencyMs}ms" to c.warn
                is SiteSearchStatus.Failed -> "× ${st.reason} · ${st.latencyMs}ms" to c.muted
            }
            Text(
                text = statusText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (siteState.status is SiteSearchStatus.ChallengeRequired) {
            Surface(
                onClick = { onVerifyCf(siteState.status.challengeUrl) },
                shape = RoundedCornerShape(5.dp),
                color = c.warn.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, c.warn),
            ) {
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .padding(horizontal = 10.dp),
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
    }
}

private fun SortOption.shortLabel(): String = when (this) {
    SortOption.RELEVANCE -> "综合"
    SortOption.SEEDERS_DESC -> "做种"
    SortOption.SIZE_DESC -> "体积↓"
    SortOption.SIZE_ASC -> "体积↑"
    SortOption.DATE_DESC -> "最新"
    SortOption.SOURCES_DESC -> "多站"
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("magnet", text))
}

private fun launchMagnetIntent(context: Context, magnetUri: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetUri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanhan.cilicili.domain.model.AggregatedMagnetItem
import com.lanhan.cilicili.domain.model.DohProvider
import com.lanhan.cilicili.domain.model.MinSizeFilter
import com.lanhan.cilicili.domain.model.SiteSearchStatus
import com.lanhan.cilicili.domain.model.SortOption
import com.lanhan.cilicili.ui.components.CloudflareVerifyDialog
import com.lanhan.cilicili.ui.components.InstrumentHairline
import com.lanhan.cilicili.ui.components.InstrumentTopBar
import com.lanhan.cilicili.ui.theme.InstrumentTheme

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (state.healthySiteCount > 0) c.ok else c.muted),
                        )
                        Text(
                            text = "$nodeSummary · $dnsLabel",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c.muted,
                        )
                    }
                    BookmarksEntryBadge(
                        count = state.bookmarks.size,
                        onClick = { viewModel.setShowBookmarksSheet(true) },
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
                onOpenBookmarks = { viewModel.setShowBookmarksSheet(true) },
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
                onSecondaryFilterChange = viewModel::onSecondaryFilterChange,
                onToggleSiteFilter = viewModel::toggleSiteFilter,
                onClearFilters = viewModel::clearSecondaryFilters,
                onOpenSiteStates = { showSiteStatusSheet = true },
                onResultClicked = viewModel::onResultClicked,
                onToggleBookmark = viewModel::toggleBookmark,
                onCopyClick = { viewModel.executeMagnetAction(it, MagnetIntentAction.COPY) },
                onDownloadClick = { viewModel.executeMagnetAction(it, MagnetIntentAction.OPEN_DOWNLOADER) },
                onLoadNextPage = viewModel::loadNextPage,
            )
        }
    }

    // Bottom Sheet: Result Action Chooser
    state.sheetItem?.let { item ->
        ResultActionBottomSheet(
            item = item,
            isBookmarked = state.bookmarkedKeys.contains(item.dedupKey),
            onDismiss = viewModel::dismissSheet,
            onToggleBookmark = { viewModel.toggleBookmark(item) },
            onCopyMagnet = { viewModel.executeMagnetAction(item, MagnetIntentAction.COPY) },
            onOpenDownloader = { viewModel.executeMagnetAction(item, MagnetIntentAction.OPEN_DOWNLOADER) },
        )
    }

    // Bottom Sheet: Per-site execution statuses + one-tap CF challenge trigger + site filter
    if (showSiteStatusSheet) {
        SiteStatusBottomSheet(
            siteStates = state.siteStates,
            selectedSiteName = state.selectedSiteName,
            onDismiss = { showSiteStatusSheet = false },
            onToggleSiteFilter = { siteName ->
                viewModel.toggleSiteFilter(siteName)
            },
            onVerifyCf = { siteState, challengeUrl ->
                showSiteStatusSheet = false
                viewModel.openCfChallengeForSite(siteState.site, challengeUrl)
            },
        )
    }

    // Bottom Sheet: Local Bookmarks / Download Later Drawer
    if (state.showBookmarksSheet) {
        BookmarksBottomSheet(
            bookmarks = state.bookmarks,
            incognitoMode = state.incognitoMode,
            resolvingDedupKey = state.resolvingDedupKey,
            onDismiss = { viewModel.setShowBookmarksSheet(false) },
            onCopyAllMagnets = viewModel::copyAllBookmarkedMagnets,
            onClearAll = viewModel::clearAllBookmarks,
            onCopyBookmark = { bookmark ->
                viewModel.executeBookmarkAction(bookmark, MagnetIntentAction.COPY)
            },
            onDownloadBookmark = { bookmark ->
                viewModel.executeBookmarkAction(bookmark, MagnetIntentAction.OPEN_DOWNLOADER)
            },
            onRemoveBookmark = viewModel::removeBookmark,
        )
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
private fun SearchHomeBody(
    state: SearchUiState,
    showSortMenu: Boolean,
    onToggleSortMenu: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onToggleIncognito: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onOpenBookmarks: () -> Unit,
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

                                // Bookmarks / Download later shortcut chip
                                val hasBookmarks = state.bookmarks.isNotEmpty()
                                Surface(
                                    onClick = onOpenBookmarks,
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (hasBookmarks) c.accent.copy(alpha = 0.14f) else c.chip,
                                    border = BorderStroke(1.dp, if (hasBookmarks) c.accent else c.hairline),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .padding(horizontal = 9.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "★ ${state.bookmarks.size}",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (hasBookmarks) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (hasBookmarks) c.accent else c.muted,
                                        )
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
    onSecondaryFilterChange: (String) -> Unit,
    onToggleSiteFilter: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onOpenSiteStates: () -> Unit,
    onResultClicked: (AggregatedMagnetItem) -> Unit,
    onToggleBookmark: (AggregatedMagnetItem) -> Unit,
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

        // Telemetry + Sort + Size Filter + Per-Site Clickable Progress Capsules Strip
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

            // Clickable site progress capsules for 1-tap "only this site" filtering
            state.siteStates.forEach { siteState ->
                SiteProgressFilterCapsule(
                    siteState = siteState,
                    isSelected = state.selectedSiteName == siteState.site.name,
                    onClick = { onToggleSiteFilter(siteState.site.name) },
                )
            }
        }

        InstrumentHairline()

        // Secondary Filter Toolbar (Inline keyword / -exclude input + active site badge)
        SecondaryFilterToolbar(
            secondaryQuery = state.secondaryFilterQuery,
            selectedSiteName = state.selectedSiteName,
            displayedCount = state.displayedResults.size,
            totalCount = state.totalAggregatedCount,
            onSecondaryQueryChange = onSecondaryFilterChange,
            onClearSiteFilter = { onToggleSiteFilter(null) },
        )

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
                    val hasAnyFilter = state.minSizeFilter != MinSizeFilter.ALL ||
                        state.secondaryFilterQuery.isNotBlank() ||
                        !state.selectedSiteName.isNullOrBlank()
                    Text(
                        text = if (state.totalAggregatedCount > 0) {
                            "结果已被当前筛选条件过滤（共 ${state.totalAggregatedCount} 条原始结果）"
                        } else {
                            "未找到「${state.activeKeyword}」相关资源"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.text,
                    )
                    Text(
                        text = if (state.totalAggregatedCount > 0) {
                            "可清除二次筛选词、站点限制或体积条件以恢复显示。"
                        } else {
                            "可点击上方状态胶囊查看站点响应，或在「节点」页测活。"
                        },
                        fontSize = 12.sp,
                        color = c.muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasAnyFilter) {
                            OutlinedButton(
                                onClick = onClearFilters,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, c.hairline),
                            ) {
                                Text("重置筛选条件", color = c.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
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
                        isBookmarked = state.bookmarkedKeys.contains(item.dedupKey),
                        isResolving = state.resolvingDedupKey == item.dedupKey,
                        onRowClick = { onResultClicked(item) },
                        onToggleBookmark = { onToggleBookmark(item) },
                        onCopyClick = { onCopyClick(item) },
                        onDownloadClick = { onDownloadClick(item) },
                        onSiteClick = { siteName -> onToggleSiteFilter(siteName) },
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

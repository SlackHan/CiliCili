package com.lanhan.cilicili.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanhan.cilicili.domain.model.AggregatedMagnetItem
import com.lanhan.cilicili.domain.model.MinSizeFilter
import com.lanhan.cilicili.domain.model.SiteExecutionState
import com.lanhan.cilicili.domain.model.SiteSearchStatus
import com.lanhan.cilicili.domain.model.SortOption
import com.lanhan.cilicili.ui.components.CiliIcons
import com.lanhan.cilicili.ui.components.CloudflareVerifyDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

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

    val topPadding by animateDpAsState(
        targetValue = if (isHome) 96.dp else 12.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "searchTopPadding",
    )

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(topPadding))

        AnimatedVisibility(
            visible = isHome,
            enter = fadeIn(tween(220)) + expandVertically(tween(280)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(260)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CiliCili",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.incognitoMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "无痕",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        UnifiedSearchPill(
            query = state.query,
            isHome = isHome,
            isSearching = state.isSearching,
            incognitoMode = state.incognitoMode,
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
            onToggleIncognito = viewModel::toggleIncognito,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isSearching || state.isLoadingMore) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Live Site Status Strip + Filter / Sort Controls
        if (state.activeKeyword.isNotEmpty()) {
            SearchProgressAndFilterStrip(
                state = state,
                showSortMenu = showSortMenu,
                onToggleSortMenu = { showSortMenu = it },
                showSizeMenu = showSizeMenu,
                onToggleSizeMenu = { showSizeMenu = it },
                onSelectSort = viewModel::setSortOption,
                onSelectMinSize = viewModel::setMinSizeFilter,
                onOpenSiteStates = { showSiteStatusSheet = true },
            )
        }

        // Main Body: History / Empty State / Results
        when {
            state.activeKeyword.isEmpty() -> {
                SearchHomeHistorySection(
                    history = state.history,
                    onClickHistory = { kw ->
                        focusManager.clearFocus()
                        viewModel.submitSearch(kw)
                    },
                    onDeleteHistory = viewModel::deleteHistoryItem,
                    onClearHistory = viewModel::clearHistory,
                )
            }
            state.displayedResults.isEmpty() && !state.isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = if (state.totalAggregatedCount > 0) {
                                "所有结果已被当前体积过滤条件隐藏"
                            } else {
                                "暂未搜到「${state.activeKeyword}」相关资源"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "可点击上方站点状态栏查看各站响应，或前往「站点」页一键测活筛选可用源。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.minSizeFilter != MinSizeFilter.ALL) {
                                OutlinedButton(onClick = { viewModel.setMinSizeFilter(MinSizeFilter.ALL) }) {
                                    Text("重置体积筛选")
                                }
                            }
                            OutlinedButton(onClick = { showSiteStatusSheet = true }) {
                                Text("查看站点状态")
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = state.displayedResults,
                        key = { it.dedupKey },
                    ) { item ->
                        MagnetResultCard(
                            item = item,
                            isResolving = state.resolvingDedupKey == item.dedupKey,
                            onCardClick = { viewModel.onResultClicked(item) },
                            onCopyClick = {
                                viewModel.executeMagnetAction(item, MagnetIntentAction.COPY)
                            },
                            onDownloadClick = {
                                viewModel.executeMagnetAction(item, MagnetIntentAction.OPEN_DOWNLOADER)
                            },
                        )
                    }
                    item(key = "load_more_footer") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            OutlinedButton(
                                onClick = viewModel::loadNextPage,
                                enabled = !state.isSearching && !state.isLoadingMore,
                            ) {
                                Text(
                                    if (state.isLoadingMore) {
                                        "正在加载第 ${state.currentPage} 页..."
                                    } else {
                                        "加载第 ${state.currentPage + 1} 页"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet: Result Action Chooser (Copy / Open Downloader / Detail Sources)
    state.sheetItem?.let { item ->
        ModalBottomSheet(onDismissRequest = viewModel::dismissSheet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                val meta = buildList {
                    if (item.sizeText.isNotBlank()) add("大小: ${item.sizeText}")
                    item.seeders?.let { add("做种: $it") }
                    if (item.dateText.isNotBlank()) add("日期: ${item.dateText}")
                    add("来源: ${item.sources.joinToString(", ") { it.siteName }}")
                }.joinToString("  ·  ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!item.infoHash.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hash: ${item.infoHash}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.executeMagnetAction(item, MagnetIntentAction.COPY) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(CiliIcons.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("复制磁力链")
                    }
                    Button(
                        onClick = { viewModel.executeMagnetAction(item, MagnetIntentAction.OPEN_DOWNLOADER) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(CiliIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("唤起下载器")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Bottom Sheet: Per-site execution statuses + one-tap CF challenge trigger
    if (showSiteStatusSheet) {
        ModalBottomSheet(onDismissRequest = { showSiteStatusSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "各站点本次响应状态 (${state.siteStates.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.siteStates, key = { it.site.id }) { siteState ->
                        SiteExecutionRow(
                            siteState = siteState,
                            onVerifyCf = { challengeUrl ->
                                showSiteStatusSheet = false
                                viewModel.openCfChallengeForSite(siteState.site, challengeUrl)
                            },
                        )
                        HorizontalDivider()
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Option A: On-Demand Sandboxed WebView CF Verification Dialog
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
private fun SearchProgressAndFilterStrip(
    state: SearchUiState,
    showSortMenu: Boolean,
    onToggleSortMenu: (Boolean) -> Unit,
    showSizeMenu: Boolean,
    onToggleSizeMenu: (Boolean) -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onSelectMinSize: (MinSizeFilter) -> Unit,
    onOpenSiteStates: () -> Unit,
) {
    val successCount = state.siteStates.count { it.status is SiteSearchStatus.Success }
    val cfCount = state.siteStates.count { it.status is SiteSearchStatus.ChallengeRequired }
    val failCount = state.siteStates.count { it.status is SiteSearchStatus.Failed }
    val runningCount = state.siteStates.count {
        it.status is SiteSearchStatus.Running || it.status is SiteSearchStatus.Pending
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onOpenSiteStates,
            label = {
                val cfTip = if (cfCount > 0) " · 盾$cfCount" else ""
                val runTip = if (runningCount > 0) " · 进行$runningCount" else ""
                Text("去重 ${state.displayedResults.size}条 · 成功$successCount/失败$failCount$cfTip$runTip")
            },
        )

        Box {
            FilterChip(
                selected = state.sortOption != SortOption.RELEVANCE,
                onClick = { onToggleSortMenu(true) },
                label = { Text(state.sortOption.label) },
            )
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
            FilterChip(
                selected = state.minSizeFilter != MinSizeFilter.ALL,
                onClick = { onToggleSizeMenu(true) },
                label = { Text(state.minSizeFilter.label) },
            )
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
}

@Composable
private fun UnifiedSearchPill(
    query: String,
    isHome: Boolean,
    isSearching: Boolean,
    incognitoMode: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onCancelSearch: () -> Unit,
    onBackHome: () -> Unit,
    onToggleIncognito: () -> Unit,
) {
    val baseContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val incognitoContainer = MaterialTheme.colorScheme.primary
        .copy(alpha = 0.12f)
        .compositeOver(baseContainer)

    val containerColor by animateColorAsState(
        targetValue = if (incognitoMode) incognitoContainer else baseContainer,
        animationSpec = tween(durationMillis = 180),
        label = "searchPillColor",
    )

    val pillBorder = if (incognitoMode) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
    } else {
        null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(52.dp),
        shape = CircleShape,
        color = containerColor,
        border = pillBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isHome) {
                IconButton(
                    onClick = onBackHome,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回首页",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = if (incognitoMode) "无痕搜索" else "搜索",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            when {
                isSearching -> {
                    Surface(
                        onClick = onCancelSearch,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        shape = RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                }
                query.isNotEmpty() -> {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        onClick = onSubmitSearch,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "搜索",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                else -> {
                    Surface(
                        onClick = onToggleIncognito,
                        shape = CircleShape,
                        color = if (incognitoMode) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = CiliIcons.Incognito,
                                contentDescription = "无痕模式",
                                tint = if (incognitoMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHomeHistorySection(
    history: List<String>,
    onClickHistory: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isEmpty()) return

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
            Text(
                text = "最近搜索",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "清空",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClearHistory)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { kw ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onClickHistory(kw) }
                            .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    ) {
                        Text(
                            text = kw,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { onDeleteHistory(kw) }
                                .padding(1.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MagnetResultCard(
    item: AggregatedMagnetItem,
    isResolving: Boolean,
    onCardClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isResolving, onClick = onCardClick),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (item.sizeText.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = item.sizeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    item.seeders?.let { seeders ->
                        val (bg, fg) = if (seeders > 0) {
                            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "做种 $seeders",
                                style = MaterialTheme.typography.labelSmall,
                                color = fg,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (item.dateText.isNotBlank()) {
                        Text(
                            text = item.dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    val sourceLabel = if (item.sources.size > 1) {
                        "${item.primarySource.siteName} +${item.sources.size - 1}"
                    } else {
                        item.primarySource.siteName
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Row {
                        IconButton(onClick = onCopyClick, modifier = Modifier.size(34.dp)) {
                            Icon(CiliIcons.Copy, contentDescription = "复制磁力链", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDownloadClick, modifier = Modifier.size(34.dp)) {
                            Icon(CiliIcons.Download, contentDescription = "唤起下载器", modifier = Modifier.size(18.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = siteState.site.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val statusText = when (val st = siteState.status) {
                SiteSearchStatus.Pending -> "排队中..."
                SiteSearchStatus.Running -> "正在请求..."
                is SiteSearchStatus.Success -> "命中 ${st.count} 条 · ${st.latencyMs}ms"
                is SiteSearchStatus.ChallengeRequired -> "触发反爬验证 · ${st.latencyMs}ms"
                is SiteSearchStatus.Failed -> "${st.reason} · ${st.latencyMs}ms"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = when (siteState.status) {
                    is SiteSearchStatus.Success -> MaterialTheme.colorScheme.primary
                    is SiteSearchStatus.ChallengeRequired -> MaterialTheme.colorScheme.tertiary
                    is SiteSearchStatus.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (siteState.status is SiteSearchStatus.ChallengeRequired) {
            OutlinedButton(onClick = { onVerifyCf(siteState.status.challengeUrl) }) {
                Icon(CiliIcons.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("去过盾")
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

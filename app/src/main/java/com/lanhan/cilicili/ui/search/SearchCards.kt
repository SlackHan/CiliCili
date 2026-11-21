package com.lanhan.cilicili.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanhan.cilicili.domain.model.AggregatedMagnetItem
import com.lanhan.cilicili.domain.model.SiteExecutionState
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.domain.model.SiteSearchStatus
import com.lanhan.cilicili.domain.model.SortOption
import com.lanhan.cilicili.ui.components.CiliIcons
import com.lanhan.cilicili.ui.theme.InstrumentTheme

@Composable
internal fun StealthModeBadge(
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
internal fun BookmarksEntryBadge(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = InstrumentTheme.colors
    val hasItems = count > 0
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(5.dp),
        color = if (hasItems) c.accent.copy(alpha = 0.14f) else c.chip,
        border = BorderStroke(1.dp, if (hasItems) c.accent else c.hairline),
    ) {
        Row(
            modifier = Modifier
                .height(26.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (hasItems) "★" else "☆",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = if (hasItems) c.accent else c.muted,
            )
            Text(
                text = "收藏 $count",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (hasItems) FontWeight.SemiBold else FontWeight.Normal,
                color = if (hasItems) c.text else c.muted,
            )
        }
    }
}

@Composable
internal fun CommandPromptGlyph(
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
internal fun NodeSignalBars(bars: List<SiteSignalBar>) {
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
internal fun SiteProgressFilterCapsule(
    siteState: SiteExecutionState,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val c = InstrumentTheme.colors
    val status = siteState.status
    val dotColor = when {
        isSelected -> c.accent
        status is SiteSearchStatus.Success && status.count > 0 -> c.ok
        status is SiteSearchStatus.Success -> c.muted
        status is SiteSearchStatus.ChallengeRequired -> c.warn
        status is SiteSearchStatus.Running || status is SiteSearchStatus.Pending -> c.muted
        else -> c.fail
    }
    val countSuffix = when (status) {
        is SiteSearchStatus.Success -> " ${status.count}"
        is SiteSearchStatus.ChallengeRequired -> " 盾"
        is SiteSearchStatus.Running -> " …"
        else -> ""
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) c.accent.copy(alpha = 0.15f) else c.chip,
        border = BorderStroke(1.dp, if (isSelected) c.accent else c.hairline),
    ) {
        Row(
            modifier = Modifier
                .height(30.dp)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = "${siteState.site.name}$countSuffix",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) c.text else c.muted,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SecondaryFilterToolbar(
    secondaryQuery: String,
    selectedSiteName: String?,
    displayedCount: Int,
    totalCount: Int,
    onSecondaryQueryChange: (String) -> Unit,
    onClearSiteFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = InstrumentTheme.colors
    val focusManager = LocalFocusManager.current
    val hasActiveFilter = secondaryQuery.isNotBlank() || !selectedSiteName.isNullOrBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!selectedSiteName.isNullOrBlank()) {
            Surface(
                onClick = onClearSiteFilter,
                shape = RoundedCornerShape(6.dp),
                color = c.accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, c.accent),
            ) {
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(c.accent),
                    )
                    Text(
                        text = "仅看:$selectedSiteName ×",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = c.text,
                        maxLines = 1,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(6.dp),
            color = c.panel,
            border = BorderStroke(1.dp, if (secondaryQuery.isNotBlank()) c.accent else c.hairline),
        ) {
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "FLT",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (secondaryQuery.isNotBlank()) c.accent else c.muted,
                )
                BasicTextField(
                    value = secondaryQuery,
                    onValueChange = onSecondaryQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.text,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (secondaryQuery.isEmpty()) {
                                Text(
                                    text = "二次筛选 / -排除词 (如 4K -720p !cam)...",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (secondaryQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onSecondaryQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "×",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c.muted,
                        )
                    }
                }
            }
        }

        if (hasActiveFilter) {
            Text(
                text = "$displayedCount/$totalCount",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = c.accent,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MagnetResultRow(
    item: AggregatedMagnetItem,
    isBookmarked: Boolean,
    isResolving: Boolean,
    onRowClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCopyClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSiteClick: ((String) -> Unit)? = null,
) {
    val c = InstrumentTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isResolving, onClick = onRowClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = item.title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isBookmarked) c.accent.copy(alpha = 0.14f) else c.chip)
                    .border(
                        width = 1.dp,
                        color = if (isBookmarked) c.accent else c.hairline,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .clickable(onClick = onToggleBookmark),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isBookmarked) "★" else "☆",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isBookmarked) c.accent else c.muted,
                )
            }
        }

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
                    .let { mod ->
                        if (onSiteClick != null) {
                            mod.clickable { onSiteClick(item.primarySource.siteName) }
                        } else {
                            mod
                        }
                    }
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
                        onClick = onToggleBookmark,
                        shape = RoundedCornerShape(5.dp),
                        color = if (isBookmarked) c.accent.copy(alpha = 0.14f) else c.chip,
                        border = BorderStroke(1.dp, if (isBookmarked) c.accent else c.hairline),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isBookmarked) "★ 已藏" else "☆ 收藏",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isBookmarked) c.accent else c.text,
                            )
                        }
                    }
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

internal fun SortOption.shortLabel(): String = when (this) {
    SortOption.RELEVANCE -> "综合"
    SortOption.SEEDERS_DESC -> "做种"
    SortOption.SIZE_DESC -> "体积↓"
    SortOption.SIZE_ASC -> "体积↑"
    SortOption.DATE_DESC -> "最新"
    SortOption.SOURCES_DESC -> "多站"
}

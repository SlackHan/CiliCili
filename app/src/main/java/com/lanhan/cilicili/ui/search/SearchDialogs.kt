package com.lanhan.cilicili.ui.search

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanhan.cilicili.data.local.BookmarkEntity
import com.lanhan.cilicili.domain.model.AggregatedMagnetItem
import com.lanhan.cilicili.domain.model.SiteExecutionState
import com.lanhan.cilicili.domain.model.SiteSearchStatus
import com.lanhan.cilicili.ui.components.InstrumentHairline
import com.lanhan.cilicili.ui.theme.InstrumentTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultActionBottomSheet(
    item: AggregatedMagnetItem,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCopyMagnet: () -> Unit,
    onOpenDownloader: () -> Unit,
) {
    val c = InstrumentTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        contentColor = c.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                )
                Surface(
                    onClick = onToggleBookmark,
                    shape = RoundedCornerShape(6.dp),
                    color = if (isBookmarked) c.accent.copy(alpha = 0.14f) else c.chip,
                    border = BorderStroke(1.dp, if (isBookmarked) c.accent else c.hairline),
                ) {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isBookmarked) "★ 已收藏" else "☆ 收藏",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBookmarked) c.accent else c.text,
                        )
                    }
                }
            }
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
                    onClick = onCopyMagnet,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, c.hairline),
                ) {
                    Text("复制磁力", color = c.text, fontFamily = FontFamily.Monospace)
                }
                Button(
                    onClick = onOpenDownloader,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SiteStatusBottomSheet(
    siteStates: List<SiteExecutionState>,
    selectedSiteName: String?,
    onDismiss: () -> Unit,
    onToggleSiteFilter: (String) -> Unit,
    onVerifyCf: (SiteExecutionState, String) -> Unit,
) {
    val c = InstrumentTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        contentColor = c.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "各站点本次响应 (${siteStates.size})",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )
                Text(
                    text = "点击站点可仅看该站",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.muted,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            InstrumentHairline()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
            ) {
                items(siteStates, key = { it.site.id }) { siteState ->
                    SiteExecutionRow(
                        siteState = siteState,
                        isSelected = selectedSiteName == siteState.site.name,
                        onToggleFilter = { onToggleSiteFilter(siteState.site.name) },
                        onVerifyCf = { challengeUrl -> onVerifyCf(siteState, challengeUrl) },
                    )
                    InstrumentHairline()
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun SiteExecutionRow(
    siteState: SiteExecutionState,
    isSelected: Boolean,
    onToggleFilter: () -> Unit,
    onVerifyCf: (String) -> Unit,
) {
    val c = InstrumentTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleFilter)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = siteState.site.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) c.accent else c.text,
                )
                if (isSelected) {
                    Text(
                        text = "· 仅看中",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = c.accent,
                    )
                }
            }
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onToggleFilter,
                shape = RoundedCornerShape(5.dp),
                color = if (isSelected) c.accent.copy(alpha = 0.14f) else c.chip,
                border = BorderStroke(1.dp, if (isSelected) c.accent else c.hairline),
            ) {
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isSelected) "取消仅看" else "仅看该站",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) c.accent else c.text,
                    )
                }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksBottomSheet(
    bookmarks: List<BookmarkEntity>,
    incognitoMode: Boolean,
    resolvingDedupKey: String?,
    onDismiss: () -> Unit,
    onCopyAllMagnets: () -> Unit,
    onClearAll: () -> Unit,
    onCopyBookmark: (BookmarkEntity) -> Unit,
    onDownloadBookmark: (BookmarkEntity) -> Unit,
    onRemoveBookmark: (String) -> Unit,
) {
    val c = InstrumentTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        contentColor = c.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "稍后下载 / 收藏夹 (${bookmarks.size})",
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = c.text,
                        )
                        if (incognitoMode) {
                            Text(
                                text = "· 无痕暂存",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = c.stealthInk,
                            )
                        }
                    }
                    Text(
                        text = "支持批量导出磁力链（换行分隔）或单条直达下载器",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.muted,
                    )
                }
            }

            if (bookmarks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        onClick = onCopyAllMagnets,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = c.accent,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "批量复制全部磁力 (${bookmarks.size})",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = c.onAccent,
                            )
                        }
                    }
                    Surface(
                        onClick = onClearAll,
                        shape = RoundedCornerShape(6.dp),
                        color = c.chip,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "清空",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = c.text,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            InstrumentHairline()

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Text(
                            text = "收藏夹为空",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = c.text,
                        )
                        Text(
                            text = "在搜索结果卡片点击「☆ 收藏」即可暂存资源，稍后可一键批量复制全部磁力链接。",
                            fontSize = 12.sp,
                            color = c.muted,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(bookmarks, key = { it.dedupKey }) { bookmark ->
                        BookmarkItemRow(
                            bookmark = bookmark,
                            isResolving = resolvingDedupKey == bookmark.dedupKey,
                            onCopyClick = { onCopyBookmark(bookmark) },
                            onDownloadClick = { onDownloadBookmark(bookmark) },
                            onRemoveClick = { onRemoveBookmark(bookmark.dedupKey) },
                        )
                        InstrumentHairline()
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookmarkItemRow(
    bookmark: BookmarkEntity,
    isResolving: Boolean,
    onCopyClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val c = InstrumentTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = bookmark.title,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val allSites = bookmark.sourceNames
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val siteLabel = when {
                allSites.size > 1 -> "${allSites.first()} +${allSites.size - 1}"
                allSites.size == 1 -> allSites.first()
                else -> bookmark.primarySiteName
            }
            if (siteLabel.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.chip)
                        .border(1.dp, c.hairline, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = siteLabel,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = c.text,
                    )
                }
            }
            if (bookmark.sizeText.isNotBlank()) {
                Text(
                    text = bookmark.sizeText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = c.text,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            bookmark.seeders?.let { seeders ->
                Text(
                    text = "↑ $seeders",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (seeders > 0) c.ok else c.muted,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            if (bookmark.dateText.isNotBlank()) {
                Text(
                    text = bookmark.dateText,
                    fontSize = 11.sp,
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
            val hashOrStatus = bookmark.infoHash?.take(8)?.uppercase()?.let { "#$it" }
                ?: if (!bookmark.magnetUri.isNullOrBlank()) "MAGNET" else "待解析"
            Text(
                text = hashOrStatus,
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = onCopyClick,
                        shape = RoundedCornerShape(5.dp),
                        color = c.chip,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "复制",
                                fontSize = 11.sp,
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
                                .height(28.dp)
                                .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "下载 ↗",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = c.text,
                            )
                        }
                    }
                    Surface(
                        onClick = onRemoveClick,
                        shape = RoundedCornerShape(5.dp),
                        color = c.chip,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "移除",
                                fontSize = 11.sp,
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

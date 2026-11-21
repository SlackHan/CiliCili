package com.lanhan.cilicili.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanhan.cilicili.ui.theme.InstrumentTheme
import kotlin.math.roundToInt

/**
 * 1dp structural horizontal divider matching the instrument hairline token.
 */
@Composable
fun InstrumentHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(InstrumentTheme.colors.hairline),
    )
}

/**
 * 1dp vertical hairline divider.
 */
@Composable
fun InstrumentVerticalHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(InstrumentTheme.colors.hairline),
    )
}

/**
 * Top telemetry strip with status bar safe-area padding.
 */
@Composable
fun InstrumentTopBar(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val c = InstrumentTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            trailing()
        }
        InstrumentHairline()
    }
}

/**
 * High-contrast rectangular hardware toggle switch with an expanded 48x36dp touch hit target.
 */
@Composable
fun InstrumentSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = InstrumentTheme.colors
    val haptic = LocalHapticFeedback.current
    val trackBg by animateColorAsState(
        targetValue = if (checked) c.activeBg else c.switchOffBg,
        animationSpec = tween(140),
        label = "switchTrack",
    )
    val knobColor by animateColorAsState(
        targetValue = if (checked) c.activeText else c.switchOffKnob,
        animationSpec = tween(140),
        label = "switchKnob",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = tween(140),
        label = "switchOffset",
    )

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(!checked)
                },
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(trackBg)
                .border(1.dp, c.hairline, RoundedCornerShape(3.dp))
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .size(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(knobColor),
            )
        }
    }
}

/**
 * Physical 3-way (or N-way) segmented switch with neutral high-contrast active state.
 */
@Composable
fun <T> InstrumentSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = InstrumentTheme.colors
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = c.panel,
        border = BorderStroke(1.dp, c.hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isSelected) c.activeBg else Color.Transparent)
                        .clickable {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(value)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) c.activeText else c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Precision vernier scale ruler slider with true-to-range ticks, active accent rail,
 * vertical pointer thumb, and haptic detent feedback on each integer step.
 */
@Composable
fun VernierRulerSlider(
    label: String,
    valueText: String,
    value: Int,
    valueRange: IntRange,
    majorTicks: List<Pair<Int, String>>,
    minorIntervals: Int = 10,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = InstrumentTheme.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val padPx = remember(density) { with(density) { 8.dp.toPx() } }
    val currentValue by rememberUpdatedState(value)
    val currentCallback by rememberUpdatedState(onValueChange)
    var widthPx by remember { mutableFloatStateOf(1f) }

    val span = (valueRange.last - valueRange.first).coerceAtLeast(1)
    val majorFractions = remember(majorTicks, valueRange) {
        majorTicks.map { (tickVal, text) ->
            val f = ((tickVal - valueRange.first).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            f to text
        }
    }

    fun updateFromX(xPx: Float) {
        val usable = (widthPx - padPx * 2f).coerceAtLeast(1f)
        val fraction = ((xPx - padPx) / usable).coerceIn(0f, 1f)
        val mapped = (valueRange.first + fraction * span).roundToInt()
            .coerceIn(valueRange.first, valueRange.last)
        if (mapped != currentValue) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentCallback(mapped)
        }
    }

    val progress = ((value - valueRange.first).toFloat() / span.toFloat()).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = c.text,
            )
            Text(
                text = valueText,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = c.accent,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(valueRange, padPx) {
                    detectTapGestures { offset -> updateFromX(offset.x) }
                }
                .pointerInput(valueRange, padPx) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        updateFromX(change.position.x)
                    }
                },
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val usableW = (size.width - padPx * 2f).coerceAtLeast(1f)
                val centerY = size.height * 0.42f
                val trackH = 2.dp.toPx()

                // Base track
                drawLine(
                    color = c.track,
                    start = Offset(padPx, centerY),
                    end = Offset(padPx + usableW, centerY),
                    strokeWidth = trackH,
                )
                // Active track
                val thumbX = padPx + usableW * progress
                drawLine(
                    color = c.accent,
                    start = Offset(padPx, centerY),
                    end = Offset(thumbX, centerY),
                    strokeWidth = trackH,
                )

                val bottomY = size.height - 2.dp.toPx()
                val minorTickH = 5.dp.toPx()
                val majorTickH = 10.dp.toPx()

                // Minor ticks
                val intervals = minorIntervals.coerceAtLeast(2)
                for (i in 0..intervals) {
                    val f = i.toFloat() / intervals.toFloat()
                    val tx = padPx + usableW * f
                    drawLine(
                        color = c.tick,
                        start = Offset(tx, bottomY - minorTickH),
                        end = Offset(tx, bottomY),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                // Major ticks anchored at exact integer value positions
                majorFractions.forEach { (f, _) ->
                    val tx = padPx + usableW * f
                    drawLine(
                        color = c.muted,
                        start = Offset(tx, bottomY - majorTickH),
                        end = Offset(tx, bottomY),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                // Precision vertical vernier thumb
                val thumbW = 11.dp.toPx()
                val thumbH = 22.dp.toPx()
                val thumbTop = centerY - thumbH / 2f
                drawRoundRect(
                    color = c.accent,
                    topLeft = Offset(thumbX - thumbW / 2f, thumbTop),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
                drawRoundRect(
                    color = c.onAccent.copy(alpha = 0.85f),
                    topLeft = Offset(thumbX - thumbW / 2f + 1.dp.toPx(), thumbTop + 1.dp.toPx()),
                    size = Size(thumbW - 2.dp.toPx(), thumbH - 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
            }
        }

        // Labels precisely centered under each major tick coordinate
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            content = {
                majorFractions.forEach { (_, text) ->
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.muted,
                        maxLines = 1,
                    )
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            val totalW = constraints.maxWidth
            val usableW = (totalW - padPx * 2f).coerceAtLeast(1f)

            layout(totalW, height) {
                placeables.forEachIndexed { idx, placeable ->
                    val f = majorFractions[idx].first
                    val anchorX = (padPx + usableW * f).roundToInt()
                    val x = (anchorX - placeable.width / 2)
                        .coerceIn(0, (totalW - placeable.width).coerceAtLeast(0))
                    placeable.placeRelative(x, 0)
                }
            }
        }
    }
}

/**
 * Precision 3-slot bottom dock bar with 2dp top accent indicator and navigation bar insets.
 */
data class DockItem(
    val index: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun InstrumentDockBar(
    items: List<DockItem>,
    modifier: Modifier = Modifier,
) {
    val c = InstrumentTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.dock)
            .navigationBarsPadding(),
    ) {
        InstrumentHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            items.forEachIndexed { i, item ->
                if (i > 0) {
                    InstrumentVerticalHairline()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = item.onClick),
                ) {
                    if (item.selected) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(c.accent)
                                .align(Alignment.TopCenter),
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.index,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (item.selected) c.accent else c.muted,
                        )
                        Text(
                            text = item.label,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (item.selected) c.text else c.muted,
                        )
                    }
                }
            }
        }
    }
}

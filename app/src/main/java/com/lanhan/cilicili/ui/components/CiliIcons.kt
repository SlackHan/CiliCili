package com.lanhan.cilicili.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight inline vector icons to avoid pulling the entire `material-icons-extended` artifact.
 */
object CiliIcons {
    val Copy: ImageVector by lazy {
        ImageVector.Builder("Copy", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(12f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(11f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(14f)
                close()
            }
        }.build()
    }

    val Download: ImageVector by lazy {
        ImageVector.Builder("Download", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 20f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                close()
                moveTo(19f, 9f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(5f)
                lineToRelative(7f, 7f)
                lineToRelative(7f, -7f)
                close()
            }
        }.build()
    }

    val Shield: ImageVector by lazy {
        ImageVector.Builder("Shield", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 1f)
                lineTo(3f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
                curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
                verticalLineTo(5f)
                lineToRelative(-9f, -4f)
                close()
                moveTo(12f, 11.99f)
                horizontalLineToRelative(7f)
                curveToRelative(-0.53f, 4.12f, -3.28f, 7.79f, -7f, 8.94f)
                verticalLineTo(12f)
                horizontalLineTo(5f)
                verticalLineTo(6.3f)
                lineToRelative(7f, -3.11f)
                verticalLineToRelative(8.8f)
                close()
            }
        }.build()
    }

    val Globe: ImageVector by lazy {
        ImageVector.Builder("Globe", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(18.93f, 11f)
                horizontalLineToRelative(-3.16f)
                curveToRelative(-0.15f, -2.54f, -0.94f, -4.85f, -2.14f, -6.58f)
                curveToRelative(2.85f, 0.78f, 5.01f, 3.4f, 5.3f, 6.58f)
                close()
                moveTo(12f, 4.04f)
                curveToRelative(1.37f, 1.82f, 2.24f, 4.29f, 2.4f, 6.96f)
                horizontalLineTo(9.6f)
                curveToRelative(0.16f, -2.67f, 1.03f, -5.14f, 2.4f, -6.96f)
                close()
                moveTo(10.37f, 4.42f)
                curveTo(9.17f, 6.15f, 8.38f, 8.46f, 8.23f, 11f)
                horizontalLineTo(5.07f)
                curveToRelative(0.29f, -3.18f, 2.45f, -5.8f, 5.3f, -6.58f)
                close()
                moveTo(5.07f, 13f)
                horizontalLineToRelative(3.16f)
                curveToRelative(0.15f, 2.54f, 0.94f, 4.85f, 2.14f, 6.58f)
                curveToRelative(-2.85f, -0.78f, -5.01f, -3.4f, -5.3f, -6.58f)
                close()
                moveTo(12f, 19.96f)
                curveToRelative(-1.37f, -1.82f, -2.24f, -4.29f, -2.4f, -6.96f)
                horizontalLineToRelative(4.8f)
                curveToRelative(-0.16f, 2.67f, -1.03f, 5.14f, -2.4f, 6.96f)
                close()
                moveTo(13.63f, 19.58f)
                curveToRelative(1.2f, -1.73f, 1.99f, -4.04f, 2.14f, -6.58f)
                horizontalLineToRelative(3.16f)
                curveToRelative(-0.29f, 3.18f, -2.45f, 5.8f, -5.3f, 6.58f)
                close()
            }
        }.build()
    }

    val Incognito: ImageVector by lazy {
        ImageVector.Builder("Incognito", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7f)
                curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f)
                curveToRelative(0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
                lineToRelative(2.92f, 2.92f)
                curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
                curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f)
                curveToRelative(-1.4f, 0f, -2.74f, 0.25f, -3.98f, 0.7f)
                lineToRelative(2.16f, 2.16f)
                curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
                close()
                moveTo(2f, 4.27f)
                lineToRelative(2.28f, 2.28f)
                lineToRelative(0.46f, 0.46f)
                curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
                curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f)
                curveToRelative(1.55f, 0f, 3.03f, -0.3f, 4.38f, -0.84f)
                lineToRelative(0.42f, 0.42f)
                lineTo(19.73f, 22f)
                lineTo(21f, 20.73f)
                lineTo(3.27f, 3f)
                lineTo(2f, 4.27f)
                close()
            }
        }.build()
    }

    val SpeedTest: ImageVector by lazy {
        ImageVector.Builder("SpeedTest", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.38f, 8.57f)
                lineToRelative(-1.23f, 1.85f)
                arcToRelative(8f, 8f, 0f, false, true, -0.22f, 7.58f)
                horizontalLineTo(5.07f)
                arcTo(8f, 8f, 0f, false, true, 15.58f, 6.85f)
                lineToRelative(1.85f, -1.23f)
                arcTo(10f, 10f, 0f, false, false, 3.35f, 19f)
                arcToRelative(2f, 2f, 0f, false, false, 1.72f, 1f)
                horizontalLineToRelative(13.85f)
                arcToRelative(2f, 2f, 0f, false, false, 1.74f, -1f)
                arcToRelative(10f, 10f, 0f, false, false, -0.27f, -10.44f)
                close()
                moveTo(10.59f, 15.41f)
                arcToRelative(2f, 2f, 0f, false, false, 2.83f, 0f)
                lineToRelative(5.66f, -8.49f)
                lineToRelative(-8.49f, 5.66f)
                arcToRelative(2f, 2f, 0f, false, false, 0f, 2.83f)
                close()
            }
        }.build()
    }
}

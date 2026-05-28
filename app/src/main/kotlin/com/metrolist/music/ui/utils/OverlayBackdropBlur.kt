/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

fun Modifier.overlayBackdropBlur(
    topHeightPx: Float,
    bottomHeightPx: Float,
    blurRadiusPx: Float,
    enabled: Boolean,
    blurLayer: GraphicsLayer?,
    onApplied: () -> Unit
): Modifier {
    if (!enabled || blurLayer == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
    } else {
        onApplied()
    }
    return drawWithContent drawContentBlock@{
        val layerSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        if (layerSize.width <= 0 || layerSize.height <= 0) {
            return@drawContentBlock
        }

        blurLayer.apply {
            record(size = layerSize) {
                this@drawContentBlock.drawContent()
            }
            renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Mirror)
        }

        val topBlurBottom = topHeightPx.coerceIn(0f, size.height)
        val bottomBlurTop = (size.height - bottomHeightPx).coerceIn(0f, size.height)

        if (bottomBlurTop > topBlurBottom) {
            clipRect(
                left = 0f,
                top = topBlurBottom,
                right = size.width,
                bottom = bottomBlurTop,
            ) {
                this@drawContentBlock.drawContent()
            }
        }

        fun drawBlurredContent(top: Float, bottom: Float) {
            if (bottom <= top) return

            clipRect(
                left = 0f,
                top = top,
                right = size.width,
                bottom = bottom,
            ) {
                drawLayer(blurLayer)
            }
        }

        drawBlurredContent(
            top = 0f,
            bottom = topBlurBottom,
        )
        drawBlurredContent(
            top = bottomBlurTop,
            bottom = size.height,
        )
    }
}

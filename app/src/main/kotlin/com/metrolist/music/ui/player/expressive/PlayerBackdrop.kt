/**
 * MuSicX Project (C) 2026
 * Licensed under GPL-3.0
 *
 * Phase 1 of the Expressive Player redesign — provides the visual
 * foundation the later phases build on:
 *
 *   * `rememberArtPalette(artUrl)` — extracts the dominant / muted colors
 *     from the current album art via Android's Palette API. Caches per-URL
 *     so we don't re-decode a thumbnail we already looked at.
 *   * `PlayerBackdrop(artUrl, palette, modifier)` — a full-screen composable
 *     that renders the album art heavily blurred + tinted with the muted
 *     dominant color. Sits BEHIND the player content and crossfades between
 *     tracks.
 *
 * These composables are self-contained; wiring into `Player.kt` is Phase 2.
 */
package com.metrolist.music.ui.player.expressive

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Color triplet extracted from the current album art. All three fields fall
 * back to sensible neutrals if the art is unavailable or decoding fails.
 *
 * @property dominant  vibrant/dominant swatch — used for the Pause pill fill
 *                     and progress bar fill.
 * @property muted     desaturated bg tone — the base tint mixed into the
 *                     blurred backdrop so it never blows out on colourful
 *                     art (e.g., a bright red album cover doesn't paint the
 *                     whole screen red — muted brings it down to a
 *                     comfortable sepia/rose).
 * @property onDominant contrasting text/icon color for anything laid on top
 *                     of [dominant] (Pause pill icon, progress-bar thumb).
 */
data class ArtPalette(
    val dominant: Color,
    val muted: Color,
    val onDominant: Color,
) {
    companion object {
        val Fallback = ArtPalette(
            dominant = Color(0xFF3F3F46),
            muted = Color(0xFF1A1A1D),
            onDominant = Color.White,
        )
    }
}

// Tiny per-process cache. Extracting palettes is cheap-ish but we hit the
// same artwork many times as tracks loop / skip forward and back, and Coil
// keeps the decoded bitmap around anyway.
private val paletteCache = ConcurrentHashMap<String, ArtPalette>()

/**
 * Loads [artUrl] via Coil, decodes a downscaled bitmap, and derives an
 * [ArtPalette] via Android's `Palette` API on a background dispatcher.
 * Returns [ArtPalette.Fallback] immediately while loading, then updates.
 */
@Composable
fun rememberArtPalette(
    artUrl: String?,
    imageLoader: ImageLoader,
): ArtPalette {
    var palette by remember(artUrl) {
        mutableStateOf(artUrl?.let { paletteCache[it] } ?: ArtPalette.Fallback)
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(artUrl) {
        if (artUrl.isNullOrBlank()) {
            palette = ArtPalette.Fallback
            return@LaunchedEffect
        }
        paletteCache[artUrl]?.let {
            palette = it
            return@LaunchedEffect
        }
        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(artUrl)
                    .allowHardware(false) // Palette needs software-backed bitmaps.
                    .size(128)             // tiny — plenty for palette extraction.
                    .build()
                (imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmapOrNull()
            }.getOrNull()
        } ?: return@LaunchedEffect

        val extracted = withContext(Dispatchers.Default) {
            runCatching {
                val p = Palette.from(bitmap).maximumColorCount(16).generate()
                val dominant = p.getVibrantColor(p.getDominantColor(0xFF3F3F46.toInt()))
                val muted = p.getDarkMutedColor(p.getMutedColor(0xFF1A1A1D.toInt()))
                ArtPalette(
                    dominant = Color(dominant),
                    muted = Color(muted),
                    onDominant = pickContrastFor(Color(dominant)),
                )
            }.getOrDefault(ArtPalette.Fallback)
        }
        paletteCache[artUrl] = extracted
        palette = extracted
    }

    return palette
}

/**
 * Full-screen blurred album-art backdrop. Renders the art heavily blurred
 * (24 dp) inside a vertical gradient of the muted palette color so the
 * player content on top remains readable regardless of how colourful the
 * artwork is. Crossfades between tracks so switching songs doesn't snap.
 *
 * Requires API 31+ for [Modifier.blur]; falls back to a solid muted color
 * on older devices (no crash, just no blur).
 */
@Composable
fun PlayerBackdrop(
    artUrl: String?,
    palette: ArtPalette,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Blurred art layer — only meaningful when we actually have art.
        Crossfade(
            targetState = artUrl,
            animationSpec = tween(durationMillis = 500),
            label = "player-backdrop-crossfade",
        ) { url ->
            if (!url.isNullOrBlank() && android.os.Build.VERSION.SDK_INT >= 31) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 32.dp),
                    colorFilter = ColorFilter.tint(
                        color = palette.muted.copy(alpha = 0.35f),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Overlay,
                    ),
                )
            }
        }
        // Top-to-bottom scrim so the player content (title, buttons, controls)
        // reads comfortably against the busiest artwork. Anchored in the
        // muted color so the transition to background feels intentional.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to palette.muted.copy(alpha = 0.35f),
                        0.55f to palette.muted.copy(alpha = 0.70f),
                        1.0f to palette.muted.copy(alpha = 0.92f),
                    ),
                ),
        )
    }
}

// Kotlin's WCAG-ish contrast picker. Returns black if the color's luminance
// is high, white otherwise. Simple and cheap.
private fun pickContrastFor(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.55f) Color.Black else Color.White
}

package nl.markmaaktmedia.drip.island.ui

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.drip.ui.theme.DripTypography
import nl.markmaaktmedia.drip.ui.theme.PaletteStyle
import nl.markmaaktmedia.drip.ui.theme.appearanceScheme

/**
 * The sizes a card needs to line up with the island it sits in. The bottom corners of a card's last row are concentric
 * with the island: the island's radius less the padding, so the gap between the two curves is even all the way round.
 */
data class CardMetrics(
    val padding: Dp = 16.dp,
    val islandBottomRadius: Dp = 40.dp,
    val buttonOuterRadius: Dp = 24.dp,
    val buttonInnerRadius: Dp = 10.dp,
    /** Room at the top of a card on a notch phone, where the status bar is. */
    val topInset: Dp = 0.dp,
)

val LocalCardMetrics = staticCompositionLocalOf { CardMetrics() }

/** White on black, like the notch itself; the accent only colours what can be pressed or moves. */
object IslandInk {
    val Primary = Color(0xFFFFFFFF)
    val Secondary = Color(0xA6FFFFFF)
    val Tertiary = Color(0x66FFFFFF)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceHigh = Color(0xFF2C2C2E)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)
}

/** A dark Material scheme around [seed], for the buttons and bars of the island. */
fun islandScheme(seed: Color): ColorScheme = appearanceScheme(seed, PaletteStyle.TONAL_SPOT, dark = true, pureBlack = true)

/**
 * The island's colours around [seed]. A new seed (another song, another kind of thing) does not jump: the whole scheme
 * slides from the old colours to the new ones, the buttons and the wave with it.
 */
@Composable
fun IslandTheme(seed: Color, metrics: CardMetrics, content: @Composable () -> Unit) {
    val target = remember(seed) { islandScheme(seed) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = nl.markmaaktmedia.drip.ui.theme.blendSchemes(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, androidx.compose.animation.core.tween(600))
    }
    val scheme = if (progress.value >= 1f) to else nl.markmaaktmedia.drip.ui.theme.blendSchemes(from, to, progress.value)
    CompositionLocalProvider(LocalCardMetrics provides metrics) {
        MaterialTheme(colorScheme = scheme, typography = DripTypography, content = content)
    }
}

/**
 * The colour a picture is about, the way Android picks one from a wallpaper: quantise, then score for something
 * colourful that covers enough of it. Worked out off the main thread and remembered per picture.
 */
@Composable
fun rememberAccentOf(bitmap: Bitmap?, fallback: Color): Color {
    var accent by remember(bitmap) { mutableStateOf(cache[bitmap?.generationId to bitmap?.hashCode()] ?: fallback) }
    LaunchedEffect(bitmap) {
        if (bitmap == null) return@LaunchedEffect
        val key = bitmap.generationId to bitmap.hashCode()
        val found = cache[key] ?: withContext(Dispatchers.Default) { accentOf(bitmap) }?.also { cache[key] = it }
        if (found != null) accent = found
    }
    return accent
}

private val cache = object : LinkedHashMap<Pair<Int?, Int?>, Color>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int?, Int?>, Color>?) = size > 16
}

private fun accentOf(bitmap: Bitmap): Color? = runCatching {
    val small = if (bitmap.width > 96 || bitmap.height > 96) Bitmap.createScaledBitmap(bitmap, 96, 96, true) else bitmap
    val soft = if (small.config == Bitmap.Config.HARDWARE) small.copy(Bitmap.Config.ARGB_8888, false) else small
    val pixels = IntArray(soft.width * soft.height)
    soft.getPixels(pixels, 0, soft.width, 0, 0, soft.width, soft.height)
    val scored = Score.score(QuantizerCelebi.quantize(pixels, 128))
    Color(scored.first())
}.getOrNull()

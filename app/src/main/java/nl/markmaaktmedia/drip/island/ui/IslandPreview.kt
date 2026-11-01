package nl.markmaaktmedia.drip.island.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.island.DeviceFit
import nl.markmaaktmedia.drip.island.IslandLayout
import nl.markmaaktmedia.drip.island.Stage

/**
 * The top of this phone's screen in miniature, with the island drawn exactly as the settings make it: the same
 * arithmetic as the real island, read from this phone's own corners, status bar and camera, only scaled down. [expanded]
 * shows the opened island with a sketch of a card in it, so the corners can be judged against the screen corners.
 */
@Composable
fun IslandPreview(settings: IslandSettings, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fit = remember { runCatching { DeviceFit.read(context) }.getOrDefault(DeviceFit.Placeholder) }
    val layout = remember(fit, settings) { IslandLayout(fit, settings) }
    val stage = if (expanded) Stage.EXPANDED else Stage.COMPACT
    val target = layout.geometry(stage, 190f * fit.density)

    // The preview moves with the same kind of spring as the island, so a slider feels like the real thing.
    val anim = remember { AnimatedGeometry(target) }
    LaunchedEffect(target, settings.bounce, settings.speed) { anim.animateTo(target, expanded, settings.bounce, settings.speed) }

    val wallTop = MaterialTheme.colorScheme.primaryContainer
    val wallBottom = MaterialTheme.colorScheme.tertiaryContainer
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val path = remember { Path() }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = constraints.maxWidth / fit.screenWidth.toFloat()
        val shownHeight = (if (expanded) layout.geometry(Stage.EXPANDED, 190f * fit.density).bottom + fit.dp(28f) else fit.statusBarHeight * 2.6f) * scale
        Canvas(Modifier.fillMaxWidth().height(with(density) { shownHeight.toDp() })) {
            val corner = (fit.screenCornerRadius ?: fit.dp(IslandLayout.DEFAULT_SCREEN_CORNER_DP).toInt()) * scale
            val screen = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        0f, 0f, size.width, size.height + corner,
                        topLeftCornerRadius = CornerRadius(corner), topRightCornerRadius = CornerRadius(corner),
                    ),
                )
            }
            clipPath(screen) {
                drawRect(Brush.verticalGradient(listOf(wallTop, wallBottom)))
                // A sketch of the status bar: the clock on the left, the icons on the right.
                val bar = fit.statusBarHeight * scale
                val dot = bar * 0.32f
                drawRoundRect(ink.copy(alpha = 0.7f), Offset(corner * 0.9f, bar / 2 - dot / 2), Size(dot * 2.6f, dot), CornerRadius(dot / 2))
                for (i in 0 until 3) {
                    drawRoundRect(ink.copy(alpha = 0.7f), Offset(size.width - corner * 0.9f - (i + 1) * dot * 1.5f, bar / 2 - dot / 2), Size(dot, dot), CornerRadius(dot / 2))
                }
                withTransform({ scale(scale, scale, Offset.Zero) }) {
                    val g = anim.value
                    g.buildPath(path)
                    drawPath(path, Color.Black)
                    if (expanded) {
                        // The bottom row of buttons, concentric with the island, to see the corners line up.
                        val pad = layout.cardPadding
                        val rowH = fit.dp(48f)
                        val top = g.bottom - pad - rowH
                        val outer = layout.buttonOuterRadius
                        val inner = layout.buttonInnerRadius
                        val w = (g.width - pad * 2 - fit.dp(8f)) / 3f
                        for (i in 0 until 3) {
                            val left = g.left + pad + i * (w + fit.dp(4f))
                            val rr = androidx.compose.ui.geometry.RoundRect(
                                left, top, left + w, top + rowH,
                                topLeftCornerRadius = CornerRadius(inner), topRightCornerRadius = CornerRadius(inner),
                                bottomLeftCornerRadius = CornerRadius(if (i == 0) outer else inner),
                                bottomRightCornerRadius = CornerRadius(if (i == 2) outer else inner),
                            )
                            drawPath(Path().apply { addRoundRect(rr) }, Color(0xFF2C2C2E))
                        }
                        val art = fit.dp(56f)
                        drawRoundRect(Color(0xFF2C2C2E), Offset(g.left + pad, g.top + pad + layout.cardTopInset), Size(art, art), CornerRadius(art / 3))
                        drawRoundRect(Color(0xFF3A3A3C), Offset(g.left + pad + art + pad, g.top + pad + layout.cardTopInset + art * 0.2f), Size(g.width * 0.4f, art * 0.22f), CornerRadius(art * 0.11f))
                        drawRoundRect(Color(0xFF2C2C2E), Offset(g.left + pad + art + pad, g.top + pad + layout.cardTopInset + art * 0.58f), Size(g.width * 0.28f, art * 0.18f), CornerRadius(art * 0.09f))
                    }
                }
            }
        }
    }
}

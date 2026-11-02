package nl.markmaaktmedia.drip.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A progress bar in the shape of Material 3 Expressive: what is done is a wave, what is left is a straight line. While
 * [moving] the wave runs along the line, the way the library's own does, so it is clear the work goes on; it is only
 * on screen while a card shows it, so it does not keep drawing in the background.
 */
@Composable
fun WavyProgress(
    fraction: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 4.dp,
    amplitude: Dp = 2.5.dp,
    wavelength: Dp = 22.dp,
    moving: Boolean = true,
) {
    val travel = rememberInfiniteTransition(label = "wave")
    // Read while drawing only, so the wave moves without the rest being built again every frame.
    val phase by travel.animateFloat(0f, if (moving) 1f else 0f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "wavePhase")
    Canvas(modifier.fillMaxWidth().height(stroke + amplitude * 2 + 2.dp)) {
        val width = size.width
        val middle = size.height / 2
        val line = stroke.toPx()
        val reach = (width * fraction.coerceIn(0f, 1f)).coerceAtLeast(line)
        // The part still to do.
        if (reach < width) {
            drawLine(trackColor, Offset(reach, middle), Offset(width, middle), strokeWidth = line, cap = StrokeCap.Round)
        }
        // The part that is done, as a wave that is flat where it starts and where it ends, so it does not jump at the ends.
        val path = Path()
        val a = amplitude.toPx()
        val length = wavelength.toPx()
        var x = 0f
        path.moveTo(0f, middle)
        while (x <= reach) {
            val edge = minOf(1f, minOf(x, reach - x) / (length / 2))
            path.lineTo(x, middle + a * edge * sin(2 * PI * (x / length - phase)).toFloat())
            x += 2f
        }
        path.lineTo(reach, middle)
        drawPath(path, color, style = Stroke(width = line, cap = StrokeCap.Round))
    }
}

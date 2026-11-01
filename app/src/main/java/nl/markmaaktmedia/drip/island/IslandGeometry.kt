package nl.markmaaktmedia.drip.island

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The island as six numbers, in pixels on the screen. Every state of the island, from the small notch to the opened card,
 * is one of these, and every movement is these numbers sliding from one set to the next. That is what keeps the shape one
 * shape: it never swaps for another, it only changes.
 *
 * [topCorner] carries both kinds of top corner in one signed number, so the change from a notch on the edge to a card
 * that floats free of it has no jump. Above zero it is the inward shoulder where a notch meets the top edge of the screen;
 * below zero it is an ordinary rounded corner of that radius; at zero the corner is square. The two formulas meet at zero,
 * see [buildPath].
 */
data class IslandGeometry(
    val centerX: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val topCorner: Float,
    val bottomRadius: Float,
) {
    val left get() = centerX - width / 2f
    val right get() = centerX + width / 2f
    val bottom get() = top + height

    /** Everything the shape covers, the shoulders included, which reach past the sides. */
    val bounds: Rect get() {
        val flare = max(0f, topCorner)
        return Rect(left - flare, top, right + flare, bottom)
    }

    /** The corners made to fit: no corner bigger than the side it sits on, and top and bottom never overlap. */
    fun clamped(): IslandGeometry {
        val w = max(width, 0f)
        val h = max(height, 0f)
        val top = topCorner.coerceIn(-min(w, h) / 2f, h / 2f)
        val bottom = bottomRadius.coerceIn(0f, min(w / 2f, h - abs(top)).coerceAtLeast(0f))
        return copy(width = w, height = h, topCorner = top, bottomRadius = bottom)
    }

    /** The outline, moved left by [originX] so it lands right in a window that starts there. */
    fun buildPath(path: Path = Path(), originX: Float = 0f, originY: Float = 0f): Path {
        val g = clamped()
        path.reset()
        if (g.width <= 0.5f || g.height <= 0.5f) return path
        val l = g.left - originX
        val r = g.right - originX
        val t = g.top - originY
        val b = g.bottom - originY
        val k = g.topCorner
        val ak = abs(k)
        val rb = g.bottomRadius
        // Control points of a quarter circle drawn as a cubic. With k on either side of zero the same expressions give
        // the inward shoulder or the outward corner, which is why the shape can pass from one to the other smoothly.
        val c = 1f - KAPPA

        path.moveTo(l - k, t)
        path.lineTo(r + k, t)
        path.cubicTo(r + k * c, t, r, t + ak * c, r, t + ak)
        path.lineTo(r, b - rb)
        path.cubicTo(r, b - rb * c, r - rb * c, b, r - rb, b)
        path.lineTo(l + rb, b)
        path.cubicTo(l + rb * c, b, l, b - rb * c, l, b - rb)
        path.lineTo(l, t + ak)
        path.cubicTo(l, t + ak * c, l - k * c, t, l - k, t)
        path.close()
        return path
    }

    companion object {
        const val KAPPA = 0.5523f
        val Zero = IslandGeometry(0f, 0f, 0f, 0f, 0f, 0f)

        fun lerp(a: IslandGeometry, b: IslandGeometry, t: Float) = IslandGeometry(
            centerX = a.centerX + (b.centerX - a.centerX) * t,
            top = a.top + (b.top - a.top) * t,
            width = a.width + (b.width - a.width) * t,
            height = a.height + (b.height - a.height) * t,
            topCorner = a.topCorner + (b.topCorner - a.topCorner) * t,
            bottomRadius = a.bottomRadius + (b.bottomRadius - a.bottomRadius) * t,
        )
    }
}

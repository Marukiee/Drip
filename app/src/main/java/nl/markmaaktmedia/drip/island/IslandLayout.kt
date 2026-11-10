package nl.markmaaktmedia.drip.island

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.view.DisplayCutout
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.RoundedCorner
import android.view.WindowInsets
import android.view.WindowManager
import nl.markmaaktmedia.drip.data.FitMode
import nl.markmaaktmedia.drip.data.IslandSettings
import kotlin.math.max
import kotlin.math.min

/**
 * What this phone looks like at the top, in pixels: read from the system, never typed in. The island is worked out from
 * these, which is how it hugs the camera of a Pixel and hangs as a notch on an Xperia without anyone measuring either.
 */
data class DeviceFit(
    val screenWidth: Int,
    val screenHeight: Int,
    val density: Float,
    val statusBarHeight: Int,
    /** The camera hole in the top edge, or null on a phone without one (or with it in another edge). */
    val cutout: Hole?,
    /** Radius of the top screen corners, or null when the phone does not say. */
    val screenCornerRadius: Int?,
    val landscape: Boolean,
    /** How far a camera on the left or right edge reaches into the screen: where it is when the phone lies on its side. */
    val sideHole: Int = 0,
) {
    fun dp(value: Float) = value * density

    /**
     * A camera this close to a corner is not supported yet: the closed pill sits on it and runs off the screen. Measured with
     * the usual pill, because a size or place someone set by hand does not change where the camera is.
     */
    val cameraInCorner: Boolean get() {
        val middle = cutout?.exactCenterX() ?: return false
        val halfPill = IslandLayout(this, IslandSettings()).compactWidth / 2f
        return middle < halfPill || screenWidth - middle < halfPill
    }

    companion object {
        /** A phone shaped like an Xperia 1 VI, for previews and tests before the real one is known. */
        val Placeholder = DeviceFit(1080, 2340, 2.625f, 76, null, 92, false)

        /** Reads the top of the screen from the system: the camera hole, the corners and the status bar. */
        fun read(context: Context): DeviceFit {
            val wm = context.getSystemService(WindowManager::class.java)
            val metrics = wm.maximumWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val density = context.resources.displayMetrics.density
            val statusBar = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            val reported = insets.displayCutout ?: display?.cutout
            val cutout = reported?.let { holeOf(it) }
            val corner = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
                ?: display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
            return DeviceFit(
                screenWidth = bounds.width(),
                screenHeight = bounds.height(),
                density = density,
                statusBarHeight = statusBar.takeIf { it > 0 } ?: (24 * density).toInt(),
                cutout = cutout,
                screenCornerRadius = corner?.takeIf { it > 0 },
                landscape = bounds.width() > bounds.height(),
                sideHole = reported?.let { sideHoleOf(it, bounds.width()) } ?: 0,
            )
        }

        /**
         * The camera itself, not the box the system reserves for it: that box runs from the top edge down to the end of
         * the status bar and is far bigger than the hole. The outline of the cutout is the real shape, so the island can
         * hug the lens.
         */
        private fun holeOf(cutout: DisplayCutout): Hole? {
            val box = cutout.boundingRectTop.takeIf { !it.isEmpty } ?: return null
            val lens = lensIn(cutout, box)
            return Hole(lens.left, lens.top, lens.right, lens.bottom)
        }

        private fun sideHoleOf(cutout: DisplayCutout, screenWidth: Int): Int {
            val left = cutout.boundingRectLeft.takeIf { !it.isEmpty }?.let { lensIn(cutout, it).right } ?: 0
            val right = cutout.boundingRectRight.takeIf { !it.isEmpty }?.let { screenWidth - lensIn(cutout, it).left } ?: 0
            return max(left, right)
        }

        private fun lensIn(cutout: DisplayCutout, box: Rect): Rect {
            val bounds = RectF()
            cutout.cutoutPath?.computeBounds(bounds, true)
            return if (bounds.intersect(RectF(box))) {
                Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
            } else {
                box
            }
        }
    }
}

/** A camera hole, in screen pixels. Its own small class so the arithmetic runs in tests without Android. */
data class Hole(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun width() = right - left
    fun height() = bottom - top
    fun exactCenterX() = (left + right) / 2f
    fun exactCenterY() = (top + bottom) / 2f
}

/** The stages of the island, from nothing at all to the opened card. */
/**
 * The stages of the island, from nothing at all to the opened card. MINI is a short announcement that only widens the
 * closed island a little (volume, ringer, charging); ALERT is one with words to read (a message).
 */
enum class Stage { HIDDEN, IDLE, COMPACT, MINI, ALERT, EXPANDED }

/**
 * Turns the settings and the phone into the shape for each [Stage]. Pure arithmetic, so it is tested without a phone.
 */
class IslandLayout(val fit: DeviceFit, val settings: IslandSettings) {

    /** Notch or pill: what the setting asks, with Auto deciding by whether there is a camera hole on top. */
    val notch: Boolean = when (settings.fitMode) {
        FitMode.NOTCH -> true
        FitMode.CUTOUT -> false
        FitMode.AUTO -> fit.cutout == null
    }

    private fun dp(v: Float) = fit.dp(v)

    private val cutout: Hole? = if (notch) null else fit.cutout

    /** The centre line of the island: the middle of the screen for a notch, the camera for a pill. */
    val centerX: Float = (cutout?.exactCenterX() ?: (fit.screenWidth / 2f)) + dp(settings.offsetXDp)

    private val topNudge = dp(settings.offsetYDp)

    /**
     * Where a pill centres itself: the middle of the camera hole, or the middle of the status bar when pill is chosen on a
     * phone without one.
     */
    private val cutoutMiddle: Float? = if (notch) null else (cutout?.exactCenterY() ?: (fit.statusBarHeight / 2f)) + topNudge

    /** Height of the closed island. On a notch phone it fills most of the status bar, around a camera it hugs the hole. */
    val idleHeight: Float = settings.idleHeightDp?.let(::dp) ?: if (cutout != null) {
        cutout.height() + 2 * dp(settings.cutoutPaddingDp)
    } else {
        max(fit.statusBarHeight - dp(6f), dp(18f))
    }

    val idleWidth: Float = settings.idleWidthDp?.let(::dp) ?: if (cutout != null) {
        max(cutout.width() + 2 * dp(settings.cutoutPaddingDp), idleHeight)
    } else {
        dp(88f)
    }

    /**
     * The camera, whatever shape the island takes: only what the display reports. A pill moved or resized by hand on a
     * phone without a hole is just a pill, so nothing makes room for a lens that is not there.
     */
    val hole: Hole? = fit.cutout

    /** The least room anything keeps from the lens, however tight the ring around it is set. */
    private val holeClearance: Float = max(dp(settings.cutoutPaddingDp), dp(3f))

    /** Tall enough for a line of text and an icon, and at least as tall as the closed island. */
    val compactHeight: Float = max(
        idleHeight,
        if (cutout != null) max(cutout.height() + 2 * dp(settings.cutoutPaddingDp), dp(30f)) else max(fit.statusBarHeight + dp(4f), dp(30f)),
    )

    val margin: Float = dp(settings.expandedMarginDp)

    /** The widest the island gets: the screen less the margin on both sides, and clear of a camera on the side. */
    val fullWidth: Float = fit.screenWidth - 2 * if (fit.sideHole > 0) max(margin, fit.sideHole + holeClearance) else margin

    val compactWidth: Float = settings.compactWidthDp?.let(::dp) ?: run {
        val usual = max(idleWidth + dp(72f), dp(150f)).coerceAtMost(fit.screenWidth * 0.45f)
        // Between the lens and the curve of the end there must be room for a clock with hours.
        val besideLens = hole?.let { it.width() + 2 * (holeClearance + compactHeight / 2f + dp(CLOCK_ROOM_DP)) } ?: 0f
        max(usual, besideLens).coerceAtMost(fullWidth)
    }

    /**
     * Top corners of the opened island. Automatic means concentric with the screen corner: the screen radius less the
     * margin, so the gap between the island and the edge of the screen keeps one width all the way round the curve.
     */
    val expandedTopRadius: Float = settings.expandedTopRadiusDp?.let(::dp)
        ?: max((fit.screenCornerRadius?.toFloat() ?: dp(DEFAULT_SCREEN_CORNER_DP)) - margin, dp(8f))

    val expandedBottomRadius: Float = dp(settings.expandedBottomRadiusDp)

    /** Inner padding of an opened card, the same on every side. */
    val cardPadding: Float = dp(16f)

    /** Outer bottom corners of buttons on the bottom row: concentric with the island unless set by hand. */
    val buttonOuterRadius: Float = settings.buttonOuterRadiusDp?.let(::dp)
        ?: max(expandedBottomRadius - cardPadding, dp(8f))

    val buttonInnerRadius: Float = dp(settings.buttonInnerRadiusDp)

    private val shoulder = dp(settings.shoulderDp)

    /**
     * Hangs from the top edge with shoulders, rather than floating. A notch phone set to round its top corners outward
     * floats like a pill, and its small drop does too.
     */
    private val hangs: Boolean get() = notch && shoulder > 0f

    /**
     * Room the content of an opened card leaves at the top: a notch phone's status bar stays readable above it, and around
     * a camera the text starts below the lens. Always, not per title: from the cover to a centred hole there is room for
     * only one short word, and a card that changes height with every song would be restless.
     */
    val cardTopInset: Float get() = if (notch && settings.expandedAttached) {
        max(fit.statusBarHeight.toFloat(), belowHole(0f, cardPadding))
    } else {
        belowHole(margin, cardPadding)
    }

    /** How far content [contentTop] under the top of a shape at [shapeTop] has to move down to start below the camera. */
    private fun belowHole(shapeTop: Float, contentTop: Float): Float =
        hole?.let { max(it.bottom + holeClearance - shapeTop - contentTop, 0f) } ?: 0f

    /**
     * The part of a closed shape the camera sits behind, in pixels from the left of that shape and with room on either
     * side, or null when the camera is somewhere else.
     */
    fun holeSpan(g: IslandGeometry): ClosedFloatingPointRange<Float>? {
        val h = hole ?: return null
        val from = h.left - holeClearance
        val to = h.right + holeClearance
        if (to <= g.left || from >= g.right || h.bottom <= g.top || h.top >= g.bottom) return null
        return (from - g.left)..(to - g.left)
    }

    private val alertBaseHeight = dp(74f)
    private val alertBottomRadius = min(expandedBottomRadius, alertBaseHeight / 2f)

    fun geometry(stage: Stage, contentHeight: Float = dp(160f)): IslandGeometry {
        val roundness = settings.collapsedRoundness.coerceIn(0f, 1f)
        return when (stage) {
            // Shrinks up into the top edge on a notch phone, into the camera around a hole, or to a point otherwise.
            Stage.HIDDEN -> when {
                notch -> collapsedShape(idleWidth * 0.7f, 0f, roundness).copy(topCorner = 0f, bottomRadius = 0f)
                cutout != null -> {
                    val size = min(cutout.width(), cutout.height()).toFloat()
                    IslandGeometry(centerX, cutout.exactCenterY() + topNudge - size / 2f, size, size, -size / 2f, size / 2f)
                }
                else -> IslandGeometry(centerX, (cutoutMiddle ?: 0f), 0f, 0f, 0f, 0f)
            }
            Stage.IDLE -> collapsedShape(idleWidth, idleHeight, roundness)
            Stage.COMPACT -> collapsedShape(compactWidth, compactHeight, roundness)
            Stage.MINI -> collapsedShape(min(compactWidth + dp(56f), fullWidth), compactHeight, roundness)
            Stage.ALERT -> {
                // A photo, two lines and a button: the status bar is covered anyway, no room is kept free for it.
                // Around a camera it grows upward until the words start below the lens; its round bottom stays the same.
                val top = if (notch) 0f else margin
                val wordsTop = alertBaseHeight - alertBottomRadius - dp(ALERT_WORDS_ABOVE_MIDDLE_DP)
                val h = alertBaseHeight + belowHole(top, wordsTop)
                val w = min(fullWidth, dp(380f))
                if (notch) {
                    IslandGeometry(centerX, top, w, h, shoulder, alertBottomRadius)
                } else {
                    // On the middle of the screen like an opened card, or a camera near a corner pushes it off the edge.
                    IslandGeometry(fit.screenWidth / 2f, top, w, h, -min(expandedTopRadius, h / 2f), alertBottomRadius)
                }
            }
            Stage.EXPANDED -> {
                val attached = notch && settings.expandedAttached
                val h = contentHeight + cardTopInset
                if (attached) {
                    IslandGeometry(fit.screenWidth / 2f + dp(settings.offsetXDp), 0f, fullWidth, h, shoulder, expandedBottomRadius)
                } else {
                    // An opened island is centred on the screen even when the camera is not, so the margin is even.
                    IslandGeometry(fit.screenWidth / 2f, margin, fullWidth, h, -expandedTopRadius, expandedBottomRadius)
                }
            }
        }.clamped()
    }

    /**
     * The small drop of a second thing, next to [main]. On a notch phone it is a little notch of its own that hangs from
     * the top edge, its shoulder just touching the island's, so the two meet in a liquid neck along the edge. Around a
     * camera it is a round bubble beside the pill.
     */
    fun secondGeometry(main: IslandGeometry): IslandGeometry {
        val h = compactHeight
        return if (hangs) {
            IslandGeometry(main.right + shoulder * 2 + dp(2f) + h / 2, main.top, h, h, shoulder, h / 2)
        } else {
            IslandGeometry(main.right + dp(6f) + h / 2, main.top, h, h, -h / 2, h / 2)
        }
    }

    /** Where the small drop waits while there is no second thing: tucked inside the right end of [main], out of sight. */
    fun tuckedSecond(main: IslandGeometry): IslandGeometry {
        val h = (main.height * 0.8f).coerceAtLeast(0f)
        // Hanging from the edge it may be flat on top; a floating pill is round all over, so the drop is a ball in the
        // middle of its round end, or its corners would stick out of the pill.
        return if (hangs) {
            IslandGeometry(main.right - main.height / 2, main.top, h, h, 0f, h / 2)
        } else {
            IslandGeometry(main.right - main.height / 2, main.top + (main.height - h) / 2, h, h, -h / 2, h / 2)
        }
    }

    /**
     * Where the small drop goes while a card or an announcement covers the island: it does not follow the island down
     * but leaves in place, drawn back up into the top edge it hangs from (a notch) or shrunk to a point (around a
     * camera). It comes back down from there when the island closes.
     */
    fun retractedSecond(): IslandGeometry {
        val out = secondGeometry(geometry(Stage.COMPACT))
        return if (hangs) {
            IslandGeometry(out.centerX, out.top, out.width * 0.4f, 0f, 0f, 0f)
        } else {
            IslandGeometry(out.centerX, out.top + out.height / 2f, 0f, 0f, 0f, 0f)
        }
    }

    private fun collapsedShape(width: Float, height: Float, roundness: Float): IslandGeometry {
        val middle = cutoutMiddle
        return if (notch || middle == null) {
            // Hangs from the top edge: the shoulders curve into the edge, the bottom is as round as the setting says.
            val bottom = (height - shoulder).coerceAtLeast(0f).coerceAtMost(height / 2f) * roundness
            IslandGeometry(centerX, topNudge.coerceAtLeast(0f), width, height, shoulder, bottom)
        } else {
            // Floats around the camera, centred on it, with round ends.
            IslandGeometry(centerX, middle - height / 2f, width, height, -(height / 2f) * roundness, (height / 2f) * roundness)
        }
    }

    companion object {
        /** What most phones without a reported corner come close to. */
        const val DEFAULT_SCREEN_CORNER_DP = 32f

        /** How far the two lines of an announcement start above the middle of its round end. */
        const val ALERT_WORDS_ABOVE_MIDDLE_DP = 22f

        /** What a clock with hours needs beside the camera, past where the curve of the end starts. */
        private const val CLOCK_ROOM_DP = 50f
    }
}

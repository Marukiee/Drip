package nl.markmaaktmedia.drip.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The windows the island lives in: accessibility overlays, the one kind of window an app may put above the status bar
 * and where it can still be touched. There are two of them, on purpose.
 *
 * The island is drawn in a window pinned to the top left corner of the screen, as wide as the screen and only as tall as
 * the island needs. It never moves, it only grows or shrinks downwards, and it lets every touch through. A window that
 * moves is drawn at its new place and moved there in two separate steps, and for a frame the island would sit in the
 * wrong spot; a window that stays put cannot do that.
 *
 * Touches are caught by a second window, invisible, laid exactly over the island. That one may move as much as it likes,
 * nobody sees it, and it keeps the rest of the status bar (the clock, the shade you pull down) working. It hands what it
 * catches to the island's view, moved into that view's coordinates.
 *
 * Both are resized only when the island starts and ends a movement, never during one: changing a window's size every
 * frame is what makes overlays stutter.
 */
class IslandWindow(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()

    /** Height of the drawing window as last laid out, so a movement can wait until the window is tall enough. */
    val laidOutHeight = mutableIntStateOf(0)

    var onOutsideTouch: (() -> Unit)? = null
    var onInsets: ((WindowInsets) -> Unit)? = null

    private val root = DrawRoot(context)
    private val drawParams = overlayParams(
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        WindowManager.LayoutParams.MATCH_PARENT, 1, "Drip island",
    )

    private val touch = TouchCatcher(context)
    private val touchParams = overlayParams(
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        1, 1, "Drip touch",
    )

    private var attached = false
    private var touchable = true

    private fun overlayParams(flags: Int, width: Int, height: Int, name: String) = WindowManager.LayoutParams(
        width, height, 0, 0,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        // Nothing may push the island down out of the status bar.
        fitInsetsTypes = 0
        windowAnimations = 0
        title = name
    }

    fun show(content: ComposeView) {
        if (attached) return
        owner.start()
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        content.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        root.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        runCatching { windowManager.addView(root, drawParams) }.onSuccess { attached = true }
        runCatching { windowManager.addView(touch, touchParams) }
    }

    fun hide() {
        if (!attached) return
        runCatching { windowManager.removeViewImmediate(root) }
        runCatching { windowManager.removeViewImmediate(touch) }
        root.removeAllViews()
        attached = false
        owner.stop()
    }

    /** The island window is pinned at the top left, so the island is drawn in screen coordinates. */
    fun originNow(): Offset = Offset.Zero

    /** Makes the drawing window [height] pixels tall. Does nothing when it already is. */
    fun setHeight(height: Int) {
        val h = height.coerceAtLeast(1)
        if (drawParams.height == h) return
        drawParams.height = h
        if (attached) runCatching { windowManager.updateViewLayout(root, drawParams) }
    }

    /** Lays the touch window over [bounds], in screen pixels. */
    fun setTouchBounds(bounds: Rect) {
        val w = bounds.width().coerceAtLeast(1)
        val h = bounds.height().coerceAtLeast(1)
        if (touchParams.x == bounds.left && touchParams.y == bounds.top && touchParams.width == w && touchParams.height == h) return
        touchParams.x = bounds.left
        touchParams.y = bounds.top
        touchParams.width = w
        touchParams.height = h
        if (attached) runCatching { windowManager.updateViewLayout(touch, touchParams) }
    }

    /** A hidden island lets every touch through, so it can never be in the way. */
    fun setTouchable(value: Boolean) {
        if (value == touchable) return
        touchable = value
        touchParams.flags = if (value) touchParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else touchParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (attached) runCatching { windowManager.updateViewLayout(touch, touchParams) }
    }

    /** Asks for the fastest refresh rate while the island moves, and lets the phone save power when it rests. */
    fun setMoving(moving: Boolean) {
        if (Build.VERSION.SDK_INT >= 35) {
            root.requestedFrameRate = if (moving) View.REQUESTED_FRAME_RATE_CATEGORY_HIGH else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
        }
    }

    private inner class DrawRoot(context: Context) : FrameLayout(context) {
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            if (height != laidOutHeight.intValue) laidOutHeight.intValue = height
        }

        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
            onInsets?.invoke(insets)
            return super.onApplyWindowInsets(insets)
        }
    }

    /** Catches touches on the island and passes them on to the island's own view, in its coordinates. */
    private inner class TouchCatcher(context: Context) : View(context) {
        private val location = IntArray(2)

        @SuppressLint("ClickableViewAccessibility")
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                onOutsideTouch?.invoke()
                return false
            }
            getLocationOnScreen(location)
            val moved = MotionEvent.obtain(event)
            moved.offsetLocation(location[0].toFloat(), location[1].toFloat())
            root.dispatchTouchEvent(moved)
            moved.recycle()
            // The touch was on the island: it is the island's, even where nothing in it reacts.
            return true
        }
    }

    /** Compose needs a lifecycle and saved state to run in a window that no activity owns. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        private var restored = false

        fun start() {
            if (!restored) {
                saved.performRestore(null)
                restored = true
            }
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.CREATED
        }
    }
}

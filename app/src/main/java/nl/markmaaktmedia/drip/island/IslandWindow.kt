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
import androidx.compose.runtime.mutableStateOf
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
 * The window the island lives in: an accessibility overlay, the one kind of window an app may put above the status bar
 * and where it can still be touched.
 *
 * The window is only as big as the island needs, so the rest of the status bar (the clock, the shade you pull down) keeps
 * working. It grows before the island opens and shrinks after it has closed, never during: changing a window's size every
 * frame is what makes overlays stutter. The island itself moves inside the window, in drawing, at the rate of the screen.
 */
class IslandWindow(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()

    /** Where the window is on the screen, as last laid out. The island is drawn relative to this. */
    val origin = mutableStateOf(Rect())

    var onOutsideTouch: (() -> Unit)? = null
    var onInsets: ((WindowInsets) -> Unit)? = null

    private val root = OverlayRoot(context)
    private val params = WindowManager.LayoutParams(
        1, 1, 0, 0,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
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
        title = "Drip island"
    }
    private var attached = false
    private var touchable = true

    fun show(content: ComposeView) {
        if (attached) return
        owner.start()
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        content.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        root.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        runCatching { windowManager.addView(root, params) }.onSuccess { attached = true }
    }

    fun hide() {
        if (!attached) return
        runCatching { windowManager.removeViewImmediate(root) }
        root.removeAllViews()
        attached = false
        owner.stop()
    }

    /** Moves and sizes the window to [bounds] in screen pixels. Does nothing when it already is there. */
    fun setBounds(bounds: Rect) {
        val w = bounds.width().coerceAtLeast(1)
        val h = bounds.height().coerceAtLeast(1)
        if (params.x == bounds.left && params.y == bounds.top && params.width == w && params.height == h) return
        params.x = bounds.left
        params.y = bounds.top
        params.width = w
        params.height = h
        if (attached) runCatching { windowManager.updateViewLayout(root, params) }
    }

    /** A hidden island lets every touch through, so it can never be in the way. */
    fun setTouchable(value: Boolean) {
        if (value == touchable) return
        touchable = value
        params.flags = if (value) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (attached) runCatching { windowManager.updateViewLayout(root, params) }
    }

    /** Asks for the fastest refresh rate while the island moves, and lets the phone save power when it rests. */
    fun setMoving(moving: Boolean) {
        if (Build.VERSION.SDK_INT >= 35) {
            root.requestedFrameRate = if (moving) View.REQUESTED_FRAME_RATE_CATEGORY_HIGH else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
        }
    }

    private inner class OverlayRoot(context: Context) : FrameLayout(context) {
        private val location = IntArray(2)

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            // Read from the laid out window, not from the parameters asked for, so the drawing and the window agree
            // even in the frame where the size changes.
            getLocationOnScreen(location)
            val now = Rect(location[0], location[1], location[0] + width, location[1] + height)
            if (now != origin.value) origin.value = now
        }

        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
            onInsets?.invoke(insets)
            return super.onApplyWindowInsets(insets)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                onOutsideTouch?.invoke()
                return false
            }
            return super.dispatchTouchEvent(event)
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

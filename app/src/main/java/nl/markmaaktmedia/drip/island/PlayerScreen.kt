package nl.markmaaktmedia.drip.island

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Whether a player has its own big screen open, with the song and its buttons already in view. A seek bar tells: the mini
 * player along the bottom of a music app only has a thin bar that cannot be dragged, the big player has one that can.
 *
 * Lyrics can hide that bar after a few seconds, leaving the song's title at the top. So a screen that is already open
 * stays open for as long as the title is high up. The mini player has the title too, but along the bottom.
 *
 * Only the windows of the app that plays are looked in, only for these two things. Nothing else is read and nothing is
 * kept.
 */
internal object PlayerScreen {
    /** A screen of a music app is a few hundred items. Far more is something else, and not worth the walk. */
    private const val MAX_NODES = 1500

    /** [title] is the song to look for as well, or null when only a seek bar counts. */
    fun isOpen(windows: List<AccessibilityWindowInfo>, packageName: String, screenWidth: Int, screenHeight: Int, title: String?): Boolean {
        var left = MAX_NODES
        val box = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            if (root.packageName?.toString() != packageName) continue
            stack.clear()
            stack.addLast(root)
            while (stack.isNotEmpty() && left-- > 0) {
                val node = stack.removeLast()
                if (!node.isVisibleToUser) continue
                if (node.rangeInfo != null) {
                    node.getBoundsInScreen(box)
                    val draggable = node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id }
                    if (isSeekBar(node.className, draggable, box.width(), screenWidth) && stillThere(node)) return true
                }
                if (title != null && isSongTitle(node.text, title)) {
                    node.getBoundsInScreen(box)
                    if (box.centerY() < screenHeight / 2 && stillThere(node)) {
                        node.getBoundsInScreen(box)
                        if (box.centerY() < screenHeight / 2) return true
                    }
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
            }
        }
        return false
    }

    /** Android answers from a cache that can lag behind: asked again, what closed a moment ago turns out to be gone. */
    private fun stillThere(node: AccessibilityNodeInfo) = node.refresh() && node.isVisibleToUser
}

/** A bar that moves the song: one that can be dragged, and wide, so a small volume slider in a corner does not count. */
internal fun isSeekBar(className: CharSequence?, draggable: Boolean, width: Int, screenWidth: Int): Boolean =
    (draggable || className?.contains("SeekBar") == true) && width * 5 >= screenWidth * 2

internal fun isSongTitle(text: CharSequence?, title: String): Boolean =
    text != null && text.trim().toString().equals(title.trim(), ignoreCase = true)

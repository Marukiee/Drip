package nl.markmaaktmedia.drip.island

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class IslandService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

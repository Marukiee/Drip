package nl.markmaaktmedia.drip.island

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.data.GestureAction
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.ui.IslandOverlay
import nl.markmaaktmedia.drip.sources.Sources

/**
 * The island's home. Android keeps an accessibility service running, which is what lets the island stay on screen
 * without a notification of its own, and only such a service may draw where the status bar is.
 *
 * It reads one kind of event: which app came to the front, so the island can step aside in the apps chosen for that.
 * It never reads what is on the screen.
 */
class IslandService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var window: IslandWindow
    private lateinit var controller: IslandController
    private lateinit var sources: Sources
    private val fit = MutableStateFlow(DeviceFit.Placeholder)
    private var visibility = Visibility()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateLocked()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val graph = applicationContext.graph
        controller = IslandController(scope, graph.hub, graph.haptics)
        controller.start(graph.prefs.island)
        sources = Sources(applicationContext)
        sources.start()
        window = IslandWindow(this)
        window.onOutsideTouch = { controller.collapse() }
        window.onInsets = ::onInsets
        fit.value = DeviceFit.read(this)
        window.show(ComposeView(this).apply {
            setContent {
                IslandOverlay(
                    controller = controller,
                    fit = fit,
                    settings = graph.prefs.island,
                    hub = graph.hub,
                    window = window,
                    onGesture = ::runGesture,
                )
            }
        })
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        updateLocked()
        scope.launch { graph.hub.demos.collect { Demos.play(applicationContext, it, controller) } }
        graph.hub.islandRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The keyboard and the system's own panels are not the app in front.
        if (pkg == packageName || pkg == "com.android.systemui" || event.className?.contains("InputMethod") == true) return
        visibility = visibility.copy(foregroundPackage = pkg)
        controller.setVisibility(visibility)
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fit.value = DeviceFit.read(this)
        visibility = visibility.copy(landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        controller.setVisibility(visibility)
    }

    private fun onInsets(insets: WindowInsets) {
        // An app in full screen hides the status bar; the island goes with it.
        val shown = insets.isVisible(WindowInsets.Type.statusBars())
        if (shown != visibility.statusBarShown) {
            visibility = visibility.copy(statusBarShown = shown)
            controller.setVisibility(visibility)
        }
        val newFit = DeviceFit.read(this)
        if (newFit != fit.value) fit.value = newFit
    }

    private fun updateLocked() {
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        visibility = visibility.copy(locked = locked)
        controller.setVisibility(visibility)
    }

    /** Carries out what a gesture on the island was set to do. */
    private fun runGesture(action: GestureAction, content: IslandContent?) {
        val graph = applicationContext.graph
        val music = (content as? IslandContent.Music)?.playing ?: graph.hub.nowPlaying.value
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.EXPAND -> controller.toggle()
            GestureAction.OPEN_APP -> openContent(content)
            GestureAction.PLAY_PAUSE -> music?.commands?.playPause()
            GestureAction.NEXT_TRACK -> music?.commands?.next()
            GestureAction.PREVIOUS_TRACK -> music?.commands?.previous()
            GestureAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GestureAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GestureAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GestureAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.LOCK_SCREEN -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            GestureAction.SCREENSHOT -> {
                // The island would be in its own screenshot; it steps aside for the moment the picture is taken.
                controller.collapse()
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
            GestureAction.TORCH -> graph.hub.torchCommands?.setOn(!graph.hub.torch.value.on)
            GestureAction.OPEN_DRIP -> startActivity(
                Intent(this, nl.markmaaktmedia.drip.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun openContent(content: IslandContent?) {
        when (content) {
            is IslandContent.Music -> content.playing.commands.openPlayer()
            is IslandContent.Call -> content.call.open?.invoke()
            is IslandContent.Live -> content.live.open?.invoke()
            is IslandContent.Notice -> content.alert.open?.invoke()
            is IslandContent.Charging, is IslandContent.Torch, null -> controller.toggle()
        }
        controller.collapse()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private var tornDown = false

    private fun tearDown() {
        if (tornDown || !::controller.isInitialized) return
        tornDown = true
        applicationContext.graph.hub.islandRunning.value = false
        runCatching { unregisterReceiver(screenReceiver) }
        sources.stop()
        window.hide()
        scope.cancel()
    }
}

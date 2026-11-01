package nl.markmaaktmedia.drip.island

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
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
        checkStatusBar()
        scope.launch { graph.hub.demos.collect { Demos.play(applicationContext, it, controller) } }
        graph.hub.islandRunning.value = true
        graph.hub.openOutput = { openOutputSwitcher(graph.hub.nowPlaying.value?.packageName) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkStatusBar()
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The keyboard and the system's own panels are not the app in front.
        if (pkg == packageName || pkg == "com.android.systemui" || event.className?.contains("InputMethod") == true) return
        visibility = visibility.copy(foregroundPackage = pkg)
        controller.setVisibility(visibility)
    }

    /**
     * Whether an app uses the full screen: the status bar is then gone from the windows on screen. Only the kind and the
     * place of each window are looked at, never what is in it. An overlay cannot ask this any other way, because the
     * island sits above the status bar and Android does not tell a window about bars that are below it.
     */
    private fun checkStatusBar() {
        val list = runCatching { windows }.getOrNull() ?: return
        val f = fit.value
        val shown = list.any { w ->
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@any false
            val r = android.graphics.Rect().also(w::getBoundsInScreen)
            // A strip along the top as wide as the screen and about as tall as the status bar. The shade, when it is
            // pulled down, is far taller and does not count.
            r.top <= 0 && r.width() >= f.screenWidth * 0.9f && r.height() in 1..(f.statusBarHeight * 2)
        }
        if (shown != visibility.statusBarShown) {
            visibility = visibility.copy(statusBarShown = shown)
            controller.setVisibility(visibility)
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fit.value = DeviceFit.read(this)
        visibility = visibility.copy(landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        controller.setVisibility(visibility)
    }

    private fun onInsets(@Suppress("UNUSED_PARAMETER") insets: WindowInsets) {
        // Insets change with a rotation or a new cutout setting: read the top of the screen again.
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
            GestureAction.OUTPUT -> openOutputSwitcher(music?.packageName)
            GestureAction.DISMISS -> controller.dismiss()
            GestureAction.OPEN_DRIP -> startActivity(
                Intent(this, nl.markmaaktmedia.drip.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Android's own panel to choose where sound plays. Android 14 opens it for any app; before that the panel of
     * Settings for media output is the nearest thing.
     */
    private fun openOutputSwitcher(playerPackage: String?) {
        controller.collapse()
        val opened = Build.VERSION.SDK_INT >= 34 && runCatching {
            android.media.MediaRouter2.getInstance(this).showSystemOutputSwitcher()
        }.getOrDefault(false)
        if (opened) return
        val intent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
            .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", playerPackage ?: packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(android.provider.Settings.Panel.ACTION_VOLUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
        applicationContext.graph.hub.openOutput = null
        runCatching { unregisterReceiver(screenReceiver) }
        sources.stop()
        window.hide()
        scope.cancel()
    }
}

package nl.markmaaktmedia.drip.island

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.media.AudioManager
import android.view.KeyEvent
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
import nl.markmaaktmedia.drip.engine.SystemPopups
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
        scope.launch {
            graph.prefs.island.collect { s ->
                SystemPopups.apply(applicationContext, s.enabled && s.showNotifications && s.hideSystemPopups)
            }
        }
        graph.hub.islandRunning.value = true
        graph.hub.openOutput = { openOutputSwitcher(graph.hub.playerInFocus.value ?: graph.hub.nowPlaying.value?.packageName) }
        graph.hub.setTyping = { on ->
            window.setTyping(on)
            controller.setTyping(on)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkStatusBar()
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()
        // A screenshot was taken: its preview and editor have their buttons along the top, right where an open card is.
        if ("screenshot" in className.lowercase() || "screenshot" in pkg || "markup" in pkg) controller.stepAside()
        // The keyboard, the system's own panels and Android asking for a permission are not the app in front.
        if (pkg == packageName || pkg == "com.android.systemui" || "InputMethod" in className || "permissioncontroller" in pkg) return
        if (pkg != visibility.foregroundPackage) {
            // Not the first app seen after a start: that is no switch, only the service catching up.
            if (visibility.foregroundPackage != null) controller.stepAside()
            graph.hub.foregroundApp.value = pkg.takeIf { it != homePackage() }
        }
        visibility = visibility.copy(foregroundPackage = pkg)
        controller.setVisibility(visibility)
    }

    /**
     * Whether an app uses the full screen: the status bar is then gone from the windows on screen. Only the kind and the
     * place of each window are looked at, never what is in it. An overlay cannot ask this any other way, because the
     * island sits above the status bar and Android does not tell a window about bars that are below it.
     */
    /** The home screen app, which is never the one using the camera even while it is in front. */
    private fun homePackage(): String? = runCatching {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
    }.getOrNull()

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

    /**
     * The volume buttons while an opened card shows the volume: Drip moves it itself, without Android's panel, which
     * would be a second volume on top of the card and lag behind it. Any other time the buttons go on to Android.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val up = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (!up && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!::controller.isInitialized) return false
        val ui = controller.ui.value
        if (ui.stage != Stage.EXPANDED) return false
        val stream = when (val c = ui.content) {
            is IslandContent.Music -> AudioManager.STREAM_MUSIC
            is IslandContent.Notice -> when (c.alert.kind) {
                AlertKind.VOLUME, AlertKind.RINGER -> AudioManager.USE_DEFAULT_STREAM_TYPE
                else -> return false
            }
            else -> return false
        }
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        // Music on a speaker elsewhere: the buttons move that speaker, as Android itself would.
        val playing = (ui.content as? IslandContent.Music)?.playing ?: graph.hub.nowPlaying.value
        if (playing?.remote?.adjustable == true && (ui.content is IslandContent.Music || stream == AudioManager.USE_DEFAULT_STREAM_TYPE)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                playing.commands.nudgeRemoteVolume(direction)
                controller.poke()
            }
            return true
        }
        val nudge = graph.hub.nudgeVolume ?: return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            nudge(stream, direction)
            controller.poke()
        }
        return true
    }

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
            is IslandContent.Charging, is IslandContent.Torch, is IslandContent.Privacy, null -> controller.toggle()
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
        SystemPopups.apply(applicationContext, false)
        applicationContext.graph.hub.islandRunning.value = false
        applicationContext.graph.hub.openOutput = null
        applicationContext.graph.hub.setTyping = null
        runCatching { unregisterReceiver(screenReceiver) }
        sources.stop()
        window.hide()
        scope.cancel()
    }
}

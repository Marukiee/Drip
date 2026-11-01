package nl.markmaaktmedia.drip.sources

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Alert
import nl.markmaaktmedia.drip.island.AlertKind
import nl.markmaaktmedia.drip.island.IslandHub

/**
 * Starts and stops everything that feeds the island apart from the notification listener: the battery, the torch,
 * the privacy dots, the volume and the system switches.
 */
class Sources(context: Context) {
    private val env = SourceEnv(context.applicationContext, context.graph.hub)
    private val parts = listOf(BatteryPart(env), CameraPart(env), AudioPart(env), SignalsPart(env))
    private var scope: CoroutineScope? = null

    fun start() {
        if (scope != null) return
        parts.forEach { safely("start ${it.javaClass.simpleName}") { it.start() } }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            it.launch {
                env.context.graph.prefs.island.collect { s ->
                    env.settings = s
                    parts.forEach { p -> safely("settings ${p.javaClass.simpleName}") { p.onSettings() } }
                }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        parts.forEach { safely("stop ${it.javaClass.simpleName}") { it.stop() } }
        env.settings = null
    }
}

/** What every part shares. Until [settings] has loaded nothing is published and nothing is announced. */
internal class SourceEnv(val context: Context, val hub: IslandHub) {
    val handler = Handler(Looper.getMainLooper())
    var settings: IslandSettings? = null

    fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    fun announce(
        id: String,
        kind: AlertKind,
        title: String,
        text: String? = null,
        @DrawableRes icon: Int? = null,
        level: Float? = null,
    ) {
        val s = settings ?: return
        hub.post(Alert(id = id, kind = kind, title = title, text = text, iconRes = icon, level = level, durationMs = s.alertDurationMs))
    }
}

/** One source. Everything it registers in [start] it lets go of in [stop], which must also work after a failed start. */
internal abstract class Part(protected val env: SourceEnv) {
    protected val context: Context get() = env.context
    protected val hub: IslandHub get() = env.hub
    protected val settings: IslandSettings? get() = env.settings

    abstract fun start()
    abstract fun stop()

    /** The settings changed, or loaded for the first time: publish again, a show switch may have flipped. */
    open fun onSettings() {}
}

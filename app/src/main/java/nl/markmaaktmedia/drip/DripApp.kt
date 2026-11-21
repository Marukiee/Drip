package nl.markmaaktmedia.drip

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.markmaaktmedia.drip.data.DripPrefs
import nl.markmaaktmedia.drip.engine.Channels
import nl.markmaaktmedia.drip.island.Haptics
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.update.UpdateRelaunch
import nl.markmaaktmedia.markupdate.MarkUpdate
import nl.markmaaktmedia.markupdate.MarkUpdateConfig

class DripApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        Channels.create(this)
        MarkUpdate.init(
            this,
            MarkUpdateConfig(
                owner = BuildConfig.GITHUB_OWNER, repo = BuildConfig.GITHUB_REPO, apkAsset = "Drip.apk",
                currentVersion = BuildConfig.VERSION_NAME, appName = "Drip", notificationIcon = R.drawable.ic_stat_drip,
                notificationChannelId = Channels.UPDATE,
                // As before: every time the app is opened, at most once a minute so switching back and forth costs nothing.
                checkIntervalMs = 60 * 1000L,
            ),
        )
        UpdateRelaunch.watch(this, graph.scope)
    }
}

/** The few long lived objects, made once per process. */
class Graph(app: Context) {
    /** Work that outlives a screen, like a settings write started from the island. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = DripPrefs(app)
    val hub = IslandHub()
    val haptics = Haptics(app)
}

val Context.graph: Graph
    get() = (applicationContext as DripApp).graph

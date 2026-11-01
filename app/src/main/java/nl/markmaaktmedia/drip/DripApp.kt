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
import nl.markmaaktmedia.drip.update.UpdateRepository

class DripApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        Channels.create(this)
    }
}

/** The few long lived objects, made once per process. */
class Graph(app: Context) {
    /** Work that outlives a screen, like a settings write started from the island. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = DripPrefs(app)
    val hub = IslandHub()
    val haptics = Haptics(app)
    val updater = UpdateRepository(app, prefs)
}

val Context.graph: Graph
    get() = (applicationContext as DripApp).graph

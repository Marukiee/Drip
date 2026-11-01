package nl.markmaaktmedia.drip.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.drip.MainActivity
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.engine.IslandKeeper
import nl.markmaaktmedia.drip.graph

/**
 * The island's switch in Quick Settings. Before the island is allowed a tap opens Drip instead, where the person is
 * shown how to allow it; with the keeper's permission it switches the island back on by itself first.
 */
class IslandTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        // Follows the switch in the app too, so the tile never shows an old state while the panel is open.
        listening = scope.launch {
            graph.prefs.island.collect { show(it.enabled, allowed()) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            if (!allowed()) {
                openApp()
                return@launch
            }
            graph.prefs.editIsland { it.copy(enabled = !it.enabled) }
            show(graph.prefs.island.first().enabled, true)
        }
    }

    /** Whether the island service may run, putting it back first when Android switched it off. */
    private suspend fun allowed(): Boolean = withContext(Dispatchers.IO) { IslandKeeper.ensure(applicationContext) }

    private fun show(enabled: Boolean, allowed: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (allowed && enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !allowed -> getString(R.string.tile_needs)
            else -> null
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // From Android 14 a plain intent is refused here; it has to be a pending one.
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

package nl.markmaaktmedia.drip.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.data.FitMode
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Demo

/** `adb shell am broadcast -a nl.markmaaktmedia.drip.DEMO --es kind music -p nl.markmaaktmedia.drip.debug` */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // --es fit NOTCH|CUTOUT|AUTO switches how the island sits, to try a notch phone on an emulator with a hole.
        intent.getStringExtra("fit")?.let { fit ->
            val mode = FitMode.entries.firstOrNull { it.name == fit.uppercase() } ?: return@let
            context.graph.scope.launch { context.graph.prefs.editIsland { it.copy(fitMode = mode) } }
        }
        val kind = intent.getStringExtra("kind")?.uppercase() ?: return
        val demo = Demo.entries.firstOrNull { it.name == kind } ?: return
        android.util.Log.d("DripIsland", "demo $demo")
        context.graph.hub.demo(demo)
    }
}

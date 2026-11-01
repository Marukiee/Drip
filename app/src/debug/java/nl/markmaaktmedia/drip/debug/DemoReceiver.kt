package nl.markmaaktmedia.drip.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Demo

/** `adb shell am broadcast -a nl.markmaaktmedia.drip.DEMO --es kind music -p nl.markmaaktmedia.drip.debug` */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind")?.uppercase() ?: return
        val demo = Demo.entries.firstOrNull { it.name == kind } ?: return
        context.graph.hub.demo(demo)
    }
}

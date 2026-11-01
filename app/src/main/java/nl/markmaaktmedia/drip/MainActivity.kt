package nl.markmaaktmedia.drip

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.engine.IslandKeeper
import nl.markmaaktmedia.drip.engine.ShizukuGrant
import nl.markmaaktmedia.drip.ui.AppRoot
import nl.markmaaktmedia.drip.ui.theme.DripTheme
import nl.markmaaktmedia.drip.ui.theme.collectAppearance

// AppCompatActivity so the language chosen in Look and language applies without a restart of the process.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ShizukuGrant.register(this)
        setContent {
            val appearance = graph.prefs.appearance.collectAppearance()
            DripTheme(appearance) { AppRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Android may have switched the island off while Drip was away; with the permission it goes straight back on.
        graph.scope.launch(Dispatchers.IO) { IslandKeeper.ensure(applicationContext) }
    }
}

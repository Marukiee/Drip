package nl.markmaaktmedia.drip

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import nl.markmaaktmedia.drip.sources.SourceGrants

/**
 * Asks Android for one permission from the island, which has no screen of its own to ask from. It shows nothing but
 * Android's own question and is gone again; the sources hear about the answer straight away. When the person said no
 * for good before, Android answers without asking, so Drip's app page opens instead, where it can still be allowed.
 */
class GrantActivity : ComponentActivity() {
    private var askedAt = 0L

    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // An answer within a moment means Android did not even show the question: it was refused for good before.
        val unasked = android.os.SystemClock.elapsedRealtime() - askedAt < 400
        if (!granted && unasked) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        SourceGrants.changed()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = intent.getStringExtra(EXTRA_PERMISSION)
        if (permission == null) {
            finish()
            return
        }
        if (savedInstanceState == null) {
            askedAt = android.os.SystemClock.elapsedRealtime()
            ask.launch(permission)
        }
    }

    companion object {
        private const val EXTRA_PERMISSION = "permission"

        fun ask(context: Context, permission: String) {
            runCatching {
                context.startActivity(
                    Intent(context, GrantActivity::class.java)
                        .putExtra(EXTRA_PERMISSION, permission)
                        // Always a fresh one, so nothing left from an earlier time comes to the front instead.
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                )
            }
        }
    }
}

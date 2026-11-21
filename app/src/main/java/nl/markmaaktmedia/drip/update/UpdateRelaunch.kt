package nl.markmaaktmedia.drip.update

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markupdate.MarkUpdate
import nl.markmaaktmedia.markupdate.UpdateState

/**
 * Drip's own step after the shared updater (markupdate) installs a new version: the app opens again. Android closes an app
 * that updates itself and does not start it, and an app may not open a screen from the background, but Drip's island
 * service is bound by the system and Android starts it again after every update; from there a screen may open.
 *
 * Only when the update was started while the app was in front, so nobody is pulled back into Drip from elsewhere.
 */
object UpdateRelaunch {
    /** Marks the moment the install starts with the app in front, and drops the mark again when it ends in this process. */
    fun watch(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            var installing = false
            MarkUpdate.get().state.collect { state ->
                val now = state is UpdateState.Installing || state is UpdateState.AwaitingConfirmation
                if (now && !installing && appIsInFront()) marks(app).edit().putLong(AT, System.currentTimeMillis()).apply()
                // Still running afterwards: it failed, or something else than Drip itself was installed (the preview).
                if (!now && installing) marks(app).edit().remove(AT).apply()
                installing = now
            }
        }
    }

    /** Called when the island's service is back. Opens the app if an update marked it moments ago. */
    fun relaunchIfUpdated(context: Context) {
        val marks = marks(context)
        val at = marks.getLong(AT, 0L)
        if (at == 0L) return
        marks.edit().remove(AT).apply()
        if (System.currentTimeMillis() - at > WINDOW_MS) return
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        runCatching { context.startActivity(open) }.onFailure { Log.w("UpdateRelaunch", "could not open the app after the update", it) }
    }

    private fun appIsInFront(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun marks(context: Context) = context.getSharedPreferences("update_relaunch", Context.MODE_PRIVATE)
    private const val AT = "at"
    /** An update takes seconds; a mark older than this is from one that never finished. */
    private const val WINDOW_MS = 3 * 60 * 1000L
}

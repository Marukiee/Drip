package nl.markmaaktmedia.drip.sources

import android.content.pm.PackageInstaller
import android.os.SystemClock
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.island.LiveInfo
import nl.markmaaktmedia.drip.island.LiveKind
import kotlin.math.roundToInt

/**
 * Whether an install session is worth showing. Installers open sessions ahead of time and leave abandoned ones behind,
 * and a system update waits for the next restart (staged), so only one that runs and moves, or moved a moment ago,
 * counts. An active one that has not moved for minutes is stuck or waiting, and no news either.
 */
fun installShown(active: Boolean, progress: Float, sinceChangeMs: Long, staged: Boolean = false): Boolean =
    !staged && ((active && sinceChangeMs < INSTALL_STUCK_MS) || (progress > 0f && progress < 1f && sinceChangeMs < INSTALL_QUIET_MS))

internal const val INSTALL_QUIET_MS = 60_000L
internal const val INSTALL_STUCK_MS = 3 * 60_000L

/** Apps being installed or updated, by any installer, with their progress. */
internal class InstallPart(env: SourceEnv) : Part(env) {
    private val installer: PackageInstaller = context.packageManager.packageInstaller
    private val apps = Apps(context)
    private val sessions = HashMap<Int, Session>()
    private var registered = false

    private class Session(val info: PackageInstaller.SessionInfo, val changedAt: Long)

    private val callback = object : PackageInstaller.SessionCallback() {
        override fun onCreated(sessionId: Int) = read(sessionId)
        override fun onBadgingChanged(sessionId: Int) = read(sessionId)
        override fun onActiveChanged(sessionId: Int, active: Boolean) = read(sessionId)
        override fun onProgressChanged(sessionId: Int, progress: Float) = read(sessionId)
        override fun onFinished(sessionId: Int, success: Boolean) {
            sessions.remove(sessionId)
            publish()
        }
    }

    // A session that went quiet without finishing drops off by itself.
    private val sweep = Runnable { publish() }

    override fun start() {
        safely("install callback") {
            installer.registerSessionCallback(callback, env.handler)
            registered = true
        }
        // Only what runs now: the rest of what getAllSessions lists is old and may never finish.
        safely("install sessions") {
            installer.allSessions.filter { it.isActive }.forEach { sessions[it.sessionId] = Session(it, SystemClock.elapsedRealtime()) }
        }
        publish()
    }

    override fun stop() {
        if (registered) safely("install callback") { installer.unregisterSessionCallback(callback) }
        registered = false
        env.handler.removeCallbacks(sweep)
        sessions.clear()
        hub.putOwnLive(PREFIX, emptyList())
    }

    override fun onSettings() = publish()

    private fun read(id: Int) {
        val info = safely("install session") { installer.getSessionInfo(id) }
        if (info == null) sessions.remove(id) else sessions[id] = Session(info, SystemClock.elapsedRealtime())
        publish()
    }

    private fun publish() {
        val s = settings ?: return
        env.handler.removeCallbacks(sweep)
        if (!s.showInstalls) {
            hub.putOwnLive(PREFIX, emptyList())
            return
        }
        val now = SystemClock.elapsedRealtime()
        val shown = sessions.values
            .filter { installShown(it.info.isActive, it.info.progress, now - it.changedAt, staged = it.info.isStaged) }
            // Android hides from other apps which app another installer is putting on the phone. "An app" is no news; the
            // store's own notification, which does name it, shows instead.
            .filter { it.info.appPackageName != null || !it.info.appLabel.isNullOrBlank() }
            .sortedByDescending { it.info.sessionId }
            // An installer that starts over opens a new session for the same app; only the newest is that install.
            .distinctBy { it.info.appPackageName ?: "session ${it.info.sessionId}" }
        hub.putOwnLive(PREFIX, shown.map { toLive(it.info) })
        // Looked at again in a while: what stopped moving, or went quiet, drops off by itself.
        if (shown.isNotEmpty()) env.handler.postDelayed(sweep, INSTALL_QUIET_MS)
    }

    private fun toLive(info: PackageInstaller.SessionInfo): LiveInfo {
        val pkg = info.appPackageName
        val installed = pkg != null && safely("installed $pkg") { context.packageManager.getPackageInfo(pkg, 0) } != null
        val label = info.appLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: pkg?.let(apps::label)
            ?: env.text(R.string.src2_an_app)
        val progress = info.progress.coerceIn(0f, 1f)
        val installerPkg = info.installerPackageName
        return LiveInfo(
            key = "$PREFIX${info.sessionId}",
            // Never Drip's own icon for an app nobody named: then the install symbol is shown instead.
            packageName = pkg ?: installerPkg ?: "",
            appLabel = installerPkg?.let(apps::label) ?: label,
            appIcon = info.appIcon ?: pkg?.let(apps::icon),
            kind = LiveKind.INSTALL,
            title = label,
            text = env.text(if (installed) R.string.src2_updating else R.string.src2_installing),
            progress = progress,
            indeterminate = progress <= 0f,
            shortText = env.text(R.string.src2_percent, (progress * 100).roundToInt()),
            open = installerPkg?.let { p -> { context.launchApp(p) } },
        )
    }

    private companion object {
        const val PREFIX = "${OWN_LIVE_PREFIX}install:"
    }
}

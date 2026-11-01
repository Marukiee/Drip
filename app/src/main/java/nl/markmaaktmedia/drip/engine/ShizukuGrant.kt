package nl.markmaaktmedia.drip.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

enum class ShizukuState {
    /** The Shizuku app is not on this phone. */
    NotInstalled,

    /** Installed, but its service is not running (it stops with every restart of the phone). */
    NotRunning,

    /** Running, and Drip has not been allowed yet. */
    NeedsPermission,

    /** The person pressed "Deny and do not ask again" in Shizuku. */
    Denied,

    /** Shizuku is older than version 11 and cannot run commands for an app. */
    TooOld,
    Ready,
}

/**
 * Shizuku, if it is there, for one thing only: running `pm grant` once, so Drip holds WRITE_SECURE_SETTINGS and the
 * [IslandKeeper] can work without Shizuku from then on. Everything is optional: without Shizuku every call answers "not
 * available" and the adb command on the same page does the same job. Nothing here throws to the caller.
 *
 * The process is started through Shizuku's own service interface, because the helper in its API that does the same is
 * private in this version.
 */
object ShizukuGrant {
    private val _state = MutableStateFlow(ShizukuState.NotInstalled)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var app: Context? = null
    private var registered = false

    private val onBinder = Shizuku.OnBinderReceivedListener { refresh() }
    private val onDead = Shizuku.OnBinderDeadListener { refresh() }
    private val onPermission = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    /** Starts listening for Shizuku coming and going. Safe to call more than once. */
    fun register(context: Context) {
        app = context.applicationContext
        if (registered) return
        registered = true
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(onBinder)
            Shizuku.addBinderDeadListener(onDead)
            Shizuku.addRequestPermissionResultListener(onPermission)
        }.onFailure { Log.w(TAG, "could not listen to Shizuku", it) }
        refresh()
    }

    /** Looks again. The page calls this while it is open, because Shizuku says nothing when it is started in its app. */
    fun refresh(): ShizukuState {
        val now = read()
        _state.value = now
        return now
    }

    private fun read(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> if (installed()) ShizukuState.NotRunning else ShizukuState.NotInstalled
            Shizuku.isPreV11() -> ShizukuState.TooOld
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.Ready
            Shizuku.shouldShowRequestPermissionRationale() -> ShizukuState.Denied
            else -> ShizukuState.NeedsPermission
        }
    } catch (_: Throwable) {
        if (installed()) ShizukuState.NotRunning else ShizukuState.NotInstalled
    }

    private fun installed(): Boolean = runCatching {
        app?.packageManager?.getPackageInfo(SHIZUKU_PACKAGE, 0) != null
    }.getOrDefault(false)

    /** Opens Shizuku's own question. The answer arrives through [state]. */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun launch(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Has Shizuku give Drip WRITE_SECURE_SETTINGS. Null when it worked, else what went wrong, in a few words. */
    suspend fun grantSecureSettings(context: Context): String? = withContext(Dispatchers.IO) {
        if (refresh() != ShizukuState.Ready) return@withContext "Shizuku is not ready"
        val command = "pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"
        try {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder()) ?: return@withContext "no Shizuku service"
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val code = process.waitFor()
            val error = runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(process.errorStream).bufferedReader().use { it.readText() }.trim()
            }.getOrDefault("")
            runCatching { process.destroy() }
            when {
                IslandKeeper.canKeep(context) -> null
                code != 0 -> error.ifBlank { "exit code $code" }
                else -> "the permission did not come through"
            }
        } catch (t: Throwable) {
            Log.w(TAG, "grant through Shizuku failed", t)
            t.message ?: t.javaClass.simpleName
        }
    }

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4712
    private const val TAG = "ShizukuGrant"
}

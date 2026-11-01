package nl.markmaaktmedia.drip.sources

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import kotlinx.coroutines.flow.update
import nl.markmaaktmedia.drip.island.TorchCommands
import nl.markmaaktmedia.drip.island.TorchInfo

/** The torch, with its strength where the phone can dim it, and the green dot of a camera in use. */
internal class CameraPart(env: SourceEnv) : Part(env) {
    private val cameras: CameraManager? = context.getSystemService(CameraManager::class.java)
    private var torchId: String? = null
    private var maxStrength = 1
    private var strength = 1
    private var torchReady = false
    private var torchOn = false
    private val busy = HashSet<String>()

    /**
     * Whether the torch really dims. Some phones report strength levels but their flash ignores them, so the first time
     * the strength is set it is read back; a phone that does not take it never shows the slider again. Null: not tried.
     */
    private val device = context.getSharedPreferences("drip_device", android.content.Context.MODE_PRIVATE)
    private var dims: Boolean? = if (device.contains(KEY_DIMS)) device.getBoolean(KEY_DIMS, false) else null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != torchId) return
            torchReady = true
            torchOn = enabled
            if (enabled) readStrength()
            publishTorch()
            publishCamera()
        }

        // A camera app took the flash.
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId != torchId) return
            torchReady = false
            torchOn = false
            publishTorch()
            publishCamera()
        }

        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            if (cameraId != torchId) return
            strength = newStrengthLevel
            if (newStrengthLevel == asked) remember(true)
            publishTorch()
        }
    }

    private val availability = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            busy.remove(cameraId)
            publishCamera()
        }

        override fun onCameraUnavailable(cameraId: String) {
            busy.add(cameraId)
            publishCamera()
        }
    }

    private val commands = object : TorchCommands {
        override fun setOn(on: Boolean) {
            val id = torchId ?: return
            safely("torch") { cameras?.setTorchMode(id, on) }
        }

        override fun setStrength(level: Int) {
            val id = torchId ?: return
            if (Build.VERSION.SDK_INT < 33 || maxStrength <= 1) {
                safely("torch") { cameras?.setTorchMode(id, true) }
                return
            }
            val wanted = level.coerceIn(1, maxStrength)
            asked = wanted
            val done = safely("torch strength") { cameras?.turnOnTorchWithStrengthLevel(id, wanted) }
            if (done == null) {
                remember(false)
                publishTorch()
                return
            }
            if (dims != null) return
            // Read back a moment later: a flash that ignores the level still reports the old one.
            env.handler.postDelayed({
                if (dims != null || asked != wanted) return@postDelayed
                val got = safely("torch strength") { cameras?.getTorchStrengthLevel(id) }
                remember(got == wanted)
                publishTorch()
            }, 500)
        }
    }

    override fun start() {
        val cm = cameras ?: return
        torchId = safely("find torch") { findTorch(cm) }
        torchId?.let { id ->
            safely("torch limits") { readLimits(cm, id) }
            safely("torch callback") { cm.registerTorchCallback(torchCallback, env.handler) }
            hub.torchCommands = commands
        }
        safely("camera callback") { cm.registerAvailabilityCallback(availability, env.handler) }
    }

    override fun stop() {
        safely("torch callback") { cameras?.unregisterTorchCallback(torchCallback) }
        safely("camera callback") { cameras?.unregisterAvailabilityCallback(availability) }
        if (hub.torchCommands === commands) hub.torchCommands = null
        torchId = null
        torchReady = false
        torchOn = false
        busy.clear()
        hub.torch.value = TorchInfo()
        hub.privacy.update { it.copy(camera = false) }
    }

    override fun onSettings() {
        publishTorch()
        publishCamera()
    }

    private fun findTorch(cm: CameraManager): String? {
        val withFlash = cm.cameraIdList.filter {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        return withFlash.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }

    private fun readLimits(cm: CameraManager, id: String) {
        if (Build.VERSION.SDK_INT < 33) return
        val c = cm.getCameraCharacteristics(id)
        maxStrength = (c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1).coerceAtLeast(1)
        strength = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: maxStrength
    }

    private fun readStrength() {
        val id = torchId ?: return
        if (Build.VERSION.SDK_INT < 33 || maxStrength <= 1) return
        safely("torch strength") { cameras?.getTorchStrengthLevel(id) }?.let { strength = it }
    }

    private var asked = 0

    private fun remember(value: Boolean) {
        if (dims == value) return
        dims = value
        device.edit().putBoolean(KEY_DIMS, value).apply()
    }

    private fun publishTorch() {
        val s = settings ?: return
        // A phone whose flash turned out not to dim gets the plain switch.
        val levels = if (dims == false) 1 else maxStrength
        hub.torch.value = if (s.showTorch) {
            TorchInfo(
                available = torchId != null && torchReady,
                on = torchOn,
                strength = strength.coerceIn(1, levels),
                maxStrength = levels,
            )
        } else {
            TorchInfo()
        }
    }

    /** The torch counts as nothing: on some phones it holds its camera while it shines. */
    private fun publishCamera() {
        val s = settings ?: return
        val inUse = s.showPrivacy && busy.any { !(torchOn && it == torchId) }
        // Which app: the one in front when it started, kept while it lasts.
        hub.privacy.update { it.copy(camera = inUse, cameraApp = if (!inUse) null else if (it.camera) it.cameraApp else hub.foregroundApp.value) }
    }
}

private const val KEY_DIMS = "torch_dims"

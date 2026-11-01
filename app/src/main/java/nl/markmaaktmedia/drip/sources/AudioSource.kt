package nl.markmaaktmedia.drip.sources

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.SystemClock
import kotlinx.coroutines.flow.update
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.island.AlertKind

/** Media volume, the ringer, headphones coming in and the orange dot of a microphone in use. */
internal class AudioPart(env: SourceEnv) : Part(env) {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val echo = EchoWindow(OWN_VOLUME_WINDOW_MS)
    private val ringer = ChangeWatch<Int>()
    private val known = HashSet<Int>()
    private var micBusy = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            safely("audio broadcast") {
                when (intent.action) {
                    VOLUME_CHANGED -> if (intent.getIntExtra(EXTRA_STREAM, -1) == AudioManager.STREAM_MUSIC) onVolume()
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> onRinger()
                }
            }
        }
    }

    // Recent Android versions no longer hand the volume broadcast to apps, so the stored volume is watched as well. The
    // system writes it to Settings.System (volume_music_speaker and friends) a moment after every change.
    private val volumeSetting = object : android.database.ContentObserver(env.handler) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            if (uri?.lastPathSegment?.startsWith("volume_music") == true) safely("volume setting") { onVolume() }
        }
    }

    private val devices = object : AudioDeviceCallback() {
        // Called at once with every device already there; those are in [known] and stay quiet.
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            addedDevices?.forEach { d ->
                if (known.add(d.id) && d.isSink && d.type in HEADSET_TYPES) onHeadphones(d)
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            removedDevices?.forEach { known.remove(it.id) }
        }
    }

    private val recording = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            micBusy = !configs.isNullOrEmpty()
            publishMic()
        }
    }

    private val setVolume: (Float) -> Unit = { level ->
        audio?.let { am ->
            safely("set volume") {
                val min = am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                echo.markOwn(SystemClock.elapsedRealtime())
                am.setStreamVolume(AudioManager.STREAM_MUSIC, volumeIndex(level, min, max), 0)
            }
        }
    }

    override fun start() {
        val am = audio ?: return
        safely("volume") { hub.musicVolume.value = musicLevel(am) }
        ringer.reset()
        safely("ringer") { ringer.update(am.ringerMode) }
        known.clear()
        safely("audio devices") { am.getDevices(AudioManager.GET_DEVICES_INPUTS or AudioManager.GET_DEVICES_OUTPUTS).forEach { known += it.id } }
        // Exported because the volume action is hidden; anything that arrives is checked against AudioManager anyway.
        safely("audio receiver") { context.listen(receiver, true, VOLUME_CHANGED, AudioManager.RINGER_MODE_CHANGED_ACTION) }
        safely("volume setting") {
            context.contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeSetting)
        }
        safely("device callback") { am.registerAudioDeviceCallback(devices, env.handler) }
        safely("recording callback") {
            am.registerAudioRecordingCallback(recording, env.handler)
            micBusy = am.activeRecordingConfigurations.isNotEmpty()
        }
        hub.setMusicVolume = setVolume
    }

    override fun stop() {
        context.unlisten(receiver)
        safely("volume setting") { context.contentResolver.unregisterContentObserver(volumeSetting) }
        safely("device callback") { audio?.unregisterAudioDeviceCallback(devices) }
        safely("recording callback") { audio?.unregisterAudioRecordingCallback(recording) }
        if (hub.setMusicVolume === setVolume) hub.setMusicVolume = null
        known.clear()
        micBusy = false
        hub.privacy.update { it.copy(microphone = false) }
    }

    override fun onSettings() = publishMic()

    private var lastLevel: Float? = null

    private fun onVolume() {
        val am = audio ?: return
        val level = musicLevel(am)
        hub.musicVolume.value = level
        // The broadcast and the stored setting can both report one change.
        if (level == lastLevel) return
        lastLevel = level
        // Drip's own slider moved it: the card already shows the bar.
        if (echo.isEcho(SystemClock.elapsedRealtime())) return
        if (settings?.alertVolume != true) return
        val icon = when {
            level <= 0f -> R.drawable.sym_volume_off
            level < 0.5f -> R.drawable.sym_volume_down
            else -> R.drawable.sym_volume_up
        }
        env.announce("volume", AlertKind.VOLUME, env.text(R.string.src_volume), icon = icon, level = level)
    }

    private fun onRinger() {
        val mode = audio?.ringerMode ?: return
        if (!ringer.update(mode) || settings?.alertRinger != true) return
        val (title, icon) = when (mode) {
            AudioManager.RINGER_MODE_VIBRATE -> R.string.src_ringer_vibrate to R.drawable.sym_vibration
            AudioManager.RINGER_MODE_SILENT -> R.string.src_ringer_silent to R.drawable.sym_notifications_off
            else -> R.string.src_ringer_normal to R.drawable.sym_notifications_active
        }
        env.announce("ringer", AlertKind.RINGER, env.text(title), icon = icon)
    }

    // Wired only: Bluetooth headphones are announced by EarbudsPart, which can add their battery. A wired plug reports the
    // phone's own model as its name, so it gets the plain word.
    private fun onHeadphones(d: AudioDeviceInfo) {
        if (settings?.alertHeadphones != true) return
        env.announce(
            "headphones", AlertKind.HEADPHONES, env.text(R.string.src_headphones), env.text(R.string.src_connected),
            R.drawable.sym_headphones,
        )
    }

    private fun publishMic() {
        val s = settings ?: return
        val inUse = s.showPrivacy && micBusy
        hub.privacy.update { it.copy(microphone = inUse, microphoneApp = if (!inUse) null else if (it.microphone) it.microphoneApp else hub.foregroundApp.value) }
    }

    private fun musicLevel(am: AudioManager) = volumeLevel(
        am.getStreamVolume(AudioManager.STREAM_MUSIC),
        am.getStreamMinVolume(AudioManager.STREAM_MUSIC),
        am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        am.isStreamMute(AudioManager.STREAM_MUSIC),
    )

    private companion object {
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val OWN_VOLUME_WINDOW_MS = 600L
        val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}

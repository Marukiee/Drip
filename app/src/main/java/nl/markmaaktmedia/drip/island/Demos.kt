package nl.markmaaktmedia.drip.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.graph

/**
 * Made up things for the island to show: for the try buttons in the app, so a person sees what a call looks like without
 * waiting for one, and for testing on an emulator that has no music or charger.
 */
object Demos {

    fun play(context: Context, demo: Demo, controller: IslandController) {
        val hub = context.graph.hub
        when (demo) {
            Demo.MUSIC -> hub.nowPlaying.value = music(context)
            Demo.CALL -> hub.call.value = call(context, incoming = false)
            Demo.INCOMING -> hub.call.value = call(context, incoming = true)
            Demo.CHARGING -> {
                hub.battery.value = BatteryInfo(level = 64, charging = true, plug = PlugType.AC, timeToFullMs = 47 * 60_000L)
                hub.post(
                    Alert(
                        id = "demo-charging", kind = AlertKind.CHARGING, title = context.getString(R.string.demo_charging),
                        text = "64%", iconRes = R.drawable.sym_bolt_filled, level = 0.64f, accent = 0xFF30D158.toInt(),
                    ),
                )
            }
            Demo.TIMER -> hub.live.value = listOf(
                LiveInfo(
                    key = "demo-timer", packageName = context.packageName, appLabel = context.getString(R.string.demo_clock),
                    kind = LiveKind.TIMER, title = context.getString(R.string.demo_timer_title), text = context.getString(R.string.demo_timer_text),
                    chronometerBase = System.currentTimeMillis() + 5 * 60_000L, countDown = true, accent = 0xFFFFB0CB.toInt(),
                    actions = listOf(IslandAction(context.getString(R.string.demo_stop), ActionKind.OTHER) { hub.live.value = emptyList() }),
                ),
            )
            Demo.ALERT -> hub.post(
                Alert(
                    id = "demo-alert-" + SystemClock.elapsedRealtime(), kind = AlertKind.NOTIFICATION,
                    title = context.getString(R.string.demo_alert_title), text = context.getString(R.string.demo_alert_text),
                    image = art(context, 0xFF7A79EE.toInt(), 0xFFFFB0CB.toInt(), 160), packageName = context.packageName,
                    accent = 0xFF7A79EE.toInt(),
                    actions = listOf(IslandAction(context.getString(R.string.demo_reply), ActionKind.OTHER) { controller.dismissAlert() }),
                ),
            )
            Demo.VOLUME -> {
                val level = ((hub.musicVolume.value + 0.1f) % 1.1f).coerceIn(0f, 1f)
                hub.musicVolume.value = level
                hub.post(Alert(id = "volume", kind = AlertKind.VOLUME, title = context.getString(R.string.demo_volume), iconRes = R.drawable.sym_volume_up, level = level))
            }
            Demo.TORCH -> {
                val on = !hub.torch.value.on
                hub.torch.value = TorchInfo(available = true, on = on, strength = 3, maxStrength = 5)
            }
            Demo.EXPAND -> if (!controller.expand()) {
                hub.nowPlaying.value = music(context)
                controller.expand()
            }
            Demo.COLLAPSE -> controller.collapse()
            Demo.CLEAR -> {
                controller.collapse()
                hub.nowPlaying.value = null
                hub.call.value = null
                hub.live.value = emptyList()
                hub.torch.value = TorchInfo()
                hub.battery.value = BatteryInfo()
            }
        }
    }

    private fun music(context: Context): NowPlaying {
        val hub = context.graph.hub
        val commands = object : MediaCommands {
            override fun playPause() {
                hub.nowPlaying.value = hub.nowPlaying.value?.let {
                    val position = it.positionMs + if (it.playing) SystemClock.elapsedRealtime() - it.positionAtElapsed else 0
                    it.copy(playing = !it.playing, positionMs = position, positionAtElapsed = SystemClock.elapsedRealtime())
                }
            }
            override fun next() = seekTo(0)
            override fun previous() = seekTo(0)
            override fun seekTo(positionMs: Long) {
                hub.nowPlaying.value = hub.nowPlaying.value?.copy(positionMs = positionMs, positionAtElapsed = SystemClock.elapsedRealtime())
            }
            override fun cycleRepeat() {
                hub.nowPlaying.value = hub.nowPlaying.value?.let { it.copy(repeat = RepeatMode.entries[((it.repeat?.ordinal ?: 0) + 1) % 3]) }
            }
            override fun toggleShuffle() {
                hub.nowPlaying.value = hub.nowPlaying.value?.let { it.copy(shuffle = it.shuffle != true) }
            }
            override fun toggleLike() {
                hub.nowPlaying.value = hub.nowPlaying.value?.let { it.copy(liked = it.liked != true) }
            }
            override fun openPlayer() = Unit
        }
        return NowPlaying(
            packageName = context.packageName, appLabel = "Drip", title = "Golden Hour", artist = "Mark Maakt Media",
            album = "Drip", art = art(context, 0xFFF2A65A.toInt(), 0xFF7A3E9D.toInt(), 360), durationMs = 4 * 60_000L + 21_000L,
            positionMs = 136_000L, positionAtElapsed = SystemClock.elapsedRealtime(), playing = true, canSeek = true,
            repeat = RepeatMode.OFF, shuffle = false, liked = false, commands = commands,
        )
    }

    private fun call(context: Context, incoming: Boolean): CallInfo {
        val hub = context.graph.hub
        val end = IslandAction(context.getString(R.string.demo_hang_up), ActionKind.HANG_UP) { hub.call.value = null }
        val actions = if (incoming) {
            listOf(
                IslandAction(context.getString(R.string.demo_decline), ActionKind.DECLINE) { hub.call.value = null },
                IslandAction(context.getString(R.string.demo_answer), ActionKind.ANSWER) {
                    hub.call.value = call(context, incoming = false)
                },
            )
        } else {
            listOf(
                IslandAction(context.getString(R.string.demo_speaker), ActionKind.SPEAKER) {},
                IslandAction(context.getString(R.string.demo_mute), ActionKind.MUTE) {},
                end,
            )
        }
        return CallInfo(
            key = "demo-call", packageName = "com.android.dialer", appLabel = context.getString(R.string.demo_phone),
            caller = "Teun", status = context.getString(if (incoming) R.string.demo_incoming else R.string.demo_ongoing),
            incoming = incoming, startedAt = if (incoming) null else System.currentTimeMillis() - 34_000L, actions = actions,
        )
    }

    /** A soft two colour picture, standing in for album art or a contact photo. */
    private fun art(context: Context, from: Int, to: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), from, to, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = RadialGradient(size * 0.3f, size * 0.75f, size * 0.35f, 0x88FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(size * 0.3f, size * 0.75f, size * 0.35f, paint)
        return bitmap
    }
}

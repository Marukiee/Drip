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
import kotlinx.coroutines.launch
import nl.markmaaktmedia.drip.graph

/**
 * Made up things for the island to show: for the try buttons in the app, so a person sees what a call looks like without
 * waiting for one, and for testing on an emulator that has no music or charger.
 */
object Demos {

    fun play(context: Context, demo: Demo, controller: IslandController) {
        val hub = context.graph.hub
        when (demo) {
            Demo.MUSIC -> {
                hub.nowPlaying.value = music(context)
                if (hub.setMusicVolume == null) hub.musicVolume.value = 0.6f
            }
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
            Demo.NAVIGATION -> hub.live.value = listOf(
                LiveInfo(
                    key = "demo-route", packageName = "com.google.android.apps.maps", appLabel = "Maps",
                    picture = turnArrow(160), kind = LiveKind.NAVIGATION, title = "200 m",
                    text = context.getString(R.string.demo_route_text), shortText = "200 m", accent = 0xFF34A853.toInt(),
                    actions = listOf(IslandAction(context.getString(R.string.demo_stop), ActionKind.OTHER) { hub.live.value = emptyList() }),
                ),
            )
            Demo.DOWNLOAD -> hub.live.value = listOf(
                LiveInfo(
                    key = "demo-download", packageName = "com.android.chrome", appLabel = "Chrome", kind = LiveKind.DOWNLOAD,
                    title = "Rushes-v0.4.2.dmg", text = context.getString(R.string.demo_download_text), progress = 0.42f,
                    shortText = "42%",
                    actions = listOf(IslandAction(context.getString(R.string.demo_cancel), ActionKind.OTHER) { hub.live.value = emptyList() }),
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
            else -> extra(context, demo, controller)
        }
    }

    /** The demos of the second round. */
    private fun extra(context: Context, demo: Demo, controller: IslandController) {
        val hub = context.graph.hub
        fun live(info: LiveInfo) {
            hub.live.value = listOf(info) + hub.live.value.filterNot { it.key == info.key }
        }
        when (demo) {
            Demo.SECOND -> {
                // Music first, then a download arrives as a second thing: the drop swells out of the island.
                hub.call.value = null
                hub.live.value = emptyList()
                hub.nowPlaying.value = music(context)
                kotlinx.coroutines.MainScope().launch {
                    kotlinx.coroutines.delay(1200)
                    live(
                        LiveInfo(
                            key = "demo-download", packageName = "com.android.chrome", appLabel = "Chrome", kind = LiveKind.DOWNLOAD,
                            title = "Rushes-v0.4.2.dmg", text = context.getString(R.string.demo_download_text), progress = 0.42f, shortText = "42%",
                        ),
                    )
                }
            }
            Demo.DELIVERY -> live(
                LiveInfo(
                    key = "demo-delivery", packageName = context.packageName, appLabel = "PostNL", kind = LiveKind.DELIVERY,
                    title = context.getString(R.string.demo_delivery_title), text = context.getString(R.string.demo_delivery_text),
                    progress = 0.62f, shortText = "14:20", detail = "14:10 - 14:30", accent = 0xFFFF9F0A.toInt(),
                    segments = listOf(ProgressSegment(0.25f, 0xFF30D158.toInt()), ProgressSegment(0.5f, 0xFFFF9F0A.toInt()), ProgressSegment(0.25f, 0xFF5F5E5A.toInt())),
                    points = listOf(ProgressPoint(0.25f), ProgressPoint(0.75f)),
                ),
            )
            Demo.CALENDAR -> live(
                LiveInfo(
                    key = "demo-calendar", packageName = context.packageName, appLabel = context.getString(R.string.demo_calendar_app),
                    kind = LiveKind.CALENDAR, title = context.getString(R.string.demo_calendar_title), detail = "15:00 · Meet",
                    chronometerBase = System.currentTimeMillis() + 8 * 60_000L, countDown = true, accent = 0xFF4285F4.toInt(),
                    actions = listOf(IslandAction(context.getString(R.string.demo_join), ActionKind.OPEN) { hub.live.value = emptyList() }),
                ),
            )
            Demo.INSTALL -> live(
                LiveInfo(
                    key = "demo-install", packageName = "com.android.vending", appLabel = "Play Store", kind = LiveKind.INSTALL,
                    title = "Tandem", text = context.getString(R.string.demo_install_text), progress = 0.7f, shortText = "70%",
                ),
            )
            Demo.REPLY -> hub.post(
                Alert(
                    id = "demo-reply-" + SystemClock.elapsedRealtime(), kind = AlertKind.NOTIFICATION,
                    title = context.getString(R.string.demo_alert_title), text = context.getString(R.string.demo_alert_text),
                    image = art(context, 0xFF7A79EE.toInt(), 0xFFFFB0CB.toInt(), 160), packageName = context.packageName, accent = 0xFF7A79EE.toInt(),
                    actions = listOf(
                        IslandAction(context.getString(R.string.demo_reply_action), ActionKind.REPLY, reply = { text ->
                            hub.post(Alert(id = "demo-sent", kind = AlertKind.OTHER, title = context.getString(R.string.demo_sent), text = text.toString(), iconRes = R.drawable.sym_send))
                        }) {},
                        IslandAction(context.getString(R.string.demo_reply), ActionKind.MARK_READ) { controller.dismissAlert() },
                    ),
                ),
            )
            Demo.EARBUDS -> hub.post(
                Alert(id = "headphones", kind = AlertKind.HEADPHONES, title = "Pixel Buds Pro", text = "80%", iconRes = R.drawable.sym_earbuds, level = 0.8f),
            )
            Demo.UNLOCK -> hub.post(Alert(id = "unlock", kind = AlertKind.UNLOCK, title = context.getString(R.string.demo_unlocked), iconRes = R.drawable.sym_lock_open, durationMs = 1400))
            Demo.PRIVACY -> hub.privacy.value = if (hub.privacy.value.any) PrivacyInfo() else
                PrivacyInfo(camera = true, microphone = true, cameraApp = "com.android.camera2", microphoneApp = "com.android.camera2")
            else -> Unit
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

    /** A white arrow that turns right, like the one a route app puts in its notification. */
    private fun turnArrow(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size / 24f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2.6f * s
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path().apply {
            moveTo(7f * s, 21f * s)
            lineTo(7f * s, 12f * s)
            quadTo(7f * s, 8f * s, 11f * s, 8f * s)
            lineTo(18f * s, 8f * s)
            moveTo(14.5f * s, 4.5f * s)
            lineTo(18f * s, 8f * s)
            lineTo(14.5f * s, 11.5f * s)
        }
        canvas.drawPath(path, paint)
        return bitmap
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

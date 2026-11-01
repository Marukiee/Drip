package nl.markmaaktmedia.drip.island.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.ActionKind
import nl.markmaaktmedia.drip.island.Alert
import nl.markmaaktmedia.drip.island.BatteryInfo
import nl.markmaaktmedia.drip.island.CallInfo
import nl.markmaaktmedia.drip.island.Haptics
import nl.markmaaktmedia.drip.island.IslandAction
import nl.markmaaktmedia.drip.island.IslandController
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.island.LiveInfo
import nl.markmaaktmedia.drip.island.LiveKind
import nl.markmaaktmedia.drip.island.PlugType
import nl.markmaaktmedia.drip.island.TorchInfo
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** A call, as the screenshot of a phone call on an island should look: who, how long, and the buttons along the bottom. */
@Composable
fun CallCard(call: CallInfo) {
    CardHeader(call.appIcon, call.packageName, call.appLabel, if (call.incoming) DripIcons.CallReceived else DripIcons.Call)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Picture(call.callerPhoto, 56.dp, CircleShape, DripIcons.Person)
        Column(Modifier.weight(1f)) {
            Title(call.caller, size = 20)
            call.status?.let { Subtitle(it) }
        }
        val start = call.startedAt
        if (start != null) {
            val now = rememberNow()
            Text(formatClock(now - start), color = IslandInk.Primary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(16.dp))
    val buttons = call.actions.map { callButton(it) }
    if (buttons.isNotEmpty()) ConcentricButtonRow(buttons)
}

@Composable
private fun callButton(action: IslandAction): RowButton = when (action.kind) {
    ActionKind.ANSWER -> RowButton(action.title, DripIcons.Call, IslandInk.Green, Color.Black, 1f, action.run)
    ActionKind.DECLINE, ActionKind.HANG_UP -> RowButton(action.title, DripIcons.CallEnd, Color(0xFFFFB4AB), Color(0xFF690005), if (action.kind == ActionKind.HANG_UP) 0.55f else 1f, action.run)
    ActionKind.SPEAKER -> RowButton(action.title, null, IslandInk.Surface, IslandInk.Primary, 1f, action.run)
    ActionKind.MUTE -> RowButton(action.title, null, IslandInk.Surface, IslandInk.Primary, 1f, action.run)
    else -> RowButton(action.title, null, IslandInk.Surface, IslandInk.Primary, 1f, action.run)
}

/** A timer, a ride, a delivery: the app, what it says, a big clock or a bar, and its buttons. */
@Composable
fun LiveCard(live: LiveInfo) {
    val accent = live.accent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    CardHeader(live.appIcon, live.packageName, live.appLabel, liveIcon(live.kind))
    Spacer(Modifier.height(12.dp))
    when (live.kind) {
        LiveKind.NAVIGATION -> RouteBody(live, accent)
        LiveKind.DOWNLOAD -> DownloadBody(live, accent)
        else -> LiveBody(live, accent)
    }
    Spacer(Modifier.height(16.dp))
    val open = stringResource(R.string.island_open)
    val buttons = live.actions.take(2).map { RowButton(it.title, null, IslandInk.Surface, IslandInk.Primary, 1f, it.run) } +
        listOfNotNull(live.open?.let { RowButton(open, DripIcons.OpenInNew, accent, Color.Black, 1f, it) })
    if (buttons.isNotEmpty()) ConcentricButtonRow(buttons)
}

/** A route: the turn arrow big on the left, the distance to it and the street it goes into. */
@Composable
private fun RouteBody(live: LiveInfo, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(accent), contentAlignment = Alignment.Center) {
            if (live.picture != null) {
                androidx.compose.foundation.Image(remember(live.picture) { live.picture.asImageBitmap() }, null, Modifier.size(44.dp))
            } else {
                Icon(DripIcons.Navigation, null, tint = Color.Black, modifier = Modifier.size(34.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(live.shortText ?: live.title, color = IslandInk.Primary, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            (live.text ?: live.title.takeIf { live.shortText != null && it != live.shortText })?.let { Subtitle(it, lines = 2) }
        }
    }
}

/** A download: what it is, how far along, and the wave of the progress. */
@Composable
private fun DownloadBody(live: LiveInfo, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(DripIcons.Download, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Title(live.title, size = 18)
            Subtitle(live.text ?: stringResource(R.string.island_downloading))
        }
        live.progress?.let {
            Text("${(it * 100).toInt()}%", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(14.dp))
    val shown by animateFloatAsState(live.progress ?: 0f, spring(1f, 300f), label = "download")
    nl.markmaaktmedia.drip.ui.components.WavyProgress(shown, accent, IslandInk.SurfaceHigh)
}

@Composable
private fun LiveBody(live: LiveInfo, accent: Color) {
    Title(live.title, size = 20)
    live.text?.let { Subtitle(it, lines = 2) }
    val base = live.chronometerBase
    if (base != null) {
        val now = rememberNow()
        Text(
            formatClock(if (live.countDown) base - now else now - base),
            color = accent, fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp),
        )
    }
    val progress = live.progress
    if (progress != null) {
        Spacer(Modifier.height(12.dp))
        nl.markmaaktmedia.drip.ui.components.WavyProgress(progress, accent, IslandInk.SurfaceHigh)
    }
}

/** Charging: a battery that fills, the level, how long it still needs, and the level again as a bar along the bottom. */
@Composable
fun ChargingCard(battery: BatteryInfo) {
    val green = IslandInk.Green
    val level by animateFloatAsState(battery.level / 100f, spring(0.8f, 200f), label = "level")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BatteryGlyph(level, green, battery.charging)
        Column(Modifier.weight(1f)) {
            Text("${battery.level}%", color = IslandInk.Primary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            val how = when (battery.plug) {
                PlugType.WIRELESS -> stringResource(R.string.island_charging_wireless)
                PlugType.USB -> stringResource(R.string.island_charging_usb)
                else -> stringResource(R.string.island_charging)
            }
            Subtitle(battery.timeToFullMs?.let { how + " · " + stringResource(R.string.island_full_in, formatSpan(it)) } ?: how)
        }
    }
    Spacer(Modifier.height(16.dp))
    val m = LocalCardMetrics.current
    val shape = RoundedCornerShape(topStart = m.buttonInnerRadius, topEnd = m.buttonInnerRadius, bottomStart = m.buttonOuterRadius, bottomEnd = m.buttonOuterRadius)
    Box(Modifier.fillMaxWidth().height(20.dp).clip(shape).background(green.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0.02f, 1f)).background(green))
    }
}

@Composable
private fun BatteryGlyph(level: Float, color: Color, charging: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(64.dp, 34.dp)) {
            val s = 2.5.dp.toPx()
            val body = Size(size.width - 5.dp.toPx(), size.height)
            drawRoundRect(color.copy(alpha = 0.5f), Offset(s / 2, s / 2), Size(body.width - s, body.height - s), CornerRadius(9.dp.toPx()), style = Stroke(s))
            val inset = 4.5.dp.toPx()
            drawRoundRect(color, Offset(inset, inset), Size((body.width - inset * 2) * level.coerceIn(0f, 1f), body.height - inset * 2), CornerRadius(5.dp.toPx()))
            drawRoundRect(color.copy(alpha = 0.5f), Offset(body.width + 1.dp.toPx(), size.height * 0.33f), Size(3.dp.toPx(), size.height * 0.34f), CornerRadius(2.dp.toPx()))
        }
        if (charging) Icon(DripIcons.BoltFilled, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** The torch: a big sun that is the switch, and the strength as a bar along the bottom when the phone can dim it. */
@Composable
fun TorchCard(torch: TorchInfo, hub: IslandHub) {
    val context = LocalContext.current
    val yellow = IslandInk.Yellow
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IslandIconButton(
            if (torch.on) DripIcons.FlashlightFilled else DripIcons.FlashlightOff,
            stringResource(R.string.island_torch),
            { hub.torchCommands?.setOn(!torch.on) },
            size = 64.dp, iconSize = 30.dp,
            container = if (torch.on) yellow else IslandInk.Surface,
            tint = if (torch.on) Color.Black else IslandInk.Primary,
            shape = MaterialShapes.Sunny.toShape(),
        )
        Column(Modifier.weight(1f)) {
            Title(stringResource(R.string.island_torch), size = 20)
            Subtitle(stringResource(if (torch.on) R.string.island_on else R.string.island_off))
        }
    }
    Spacer(Modifier.height(16.dp))
    if (torch.maxStrength > 1) {
        val m = LocalCardMetrics.current
        var local by remember { mutableStateOf<Float?>(null) }
        val fraction = local ?: (torch.strength.toFloat() / torch.maxStrength)
        val shown by animateFloatAsState(fraction, spring(1f, 900f), label = "strength")
        fun set(f: Float) {
            val level = (f.coerceIn(0f, 1f) * torch.maxStrength).toInt().coerceIn(1, torch.maxStrength)
            local = level.toFloat() / torch.maxStrength
            if (level != torch.strength) {
                context.graph.haptics.play(Haptics.Kind.TICK)
                hub.torchCommands?.setStrength(level)
            }
        }
        val shape = RoundedCornerShape(topStart = m.buttonInnerRadius, topEnd = m.buttonInnerRadius, bottomStart = m.buttonOuterRadius, bottomEnd = m.buttonOuterRadius)
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .background(IslandInk.Surface)
                .pointerInput(torch.maxStrength) { detectTapGestures { set(it.x / size.width) } }
                .pointerInput(torch.maxStrength) {
                    detectHorizontalDragGestures(onDragEnd = { local = null }, onDragCancel = { local = null }) { change, _ ->
                        change.consume()
                        set(change.position.x / size.width)
                    }
                },
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown.coerceIn(0.001f, 1f)).background(yellow))
            Icon(DripIcons.Brightness, null, tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(22.dp))
        }
    } else {
        ConcentricButtonRow(
            listOf(
                RowButton(
                    stringResource(if (torch.on) R.string.island_turn_off else R.string.island_turn_on), null,
                    if (torch.on) yellow else IslandInk.Surface, if (torch.on) Color.Black else IslandInk.Primary, 1f,
                ) { hub.torchCommands?.setOn(!torch.on) },
            ),
        )
    }
}

/** An opened announcement: the whole message and its buttons. */
@Composable
fun NoticeCard(alert: Alert, controller: IslandController) {
    val accent = alertAccent(alert)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            alert.image != null -> Picture(alert.image, 48.dp, CircleShape)
            alert.iconRes != null -> Box(Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.ui.res.painterResource(alert.iconRes), null, tint = accent, modifier = Modifier.size(26.dp))
            }
            else -> AppIcon(null, alert.packageName, 44.dp)
        }
        Column(Modifier.weight(1f)) {
            Title(alert.title, size = 18)
        }
        if (alert.image != null) AppIcon(null, alert.packageName, 22.dp)
    }
    alert.text?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = IslandInk.Primary.copy(alpha = 0.85f), fontSize = 15.sp, lineHeight = 20.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
    }
    alert.level?.let {
        Spacer(Modifier.height(12.dp))
        LevelBar(it, accent, Modifier.fillMaxWidth().height(8.dp))
    }
    Spacer(Modifier.height(16.dp))
    val open = stringResource(R.string.island_open)
    val buttons = alert.actions.take(2).map { action ->
        RowButton(action.title, null, IslandInk.Surface, IslandInk.Primary, 1f) {
            action.run()
            controller.dismissAlert()
        }
    } + listOfNotNull(
        alert.open?.let { run ->
            RowButton(open, DripIcons.OpenInNew, accent, Color.Black, 1f) {
                run()
                controller.dismissAlert()
            }
        },
    )
    if (buttons.isNotEmpty()) ConcentricButtonRow(buttons)
}

/** The app a card belongs to, its name, and a small icon for what kind of thing it is. */
@Composable
private fun CardHeader(appIcon: android.graphics.Bitmap?, packageName: String, label: String, kind: androidx.compose.ui.graphics.painter.Painter) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(appIcon, packageName, 22.dp)
        Text(label, color = IslandInk.Secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
        Icon(kind, null, tint = IslandInk.Secondary, modifier = Modifier.size(20.dp))
    }
}

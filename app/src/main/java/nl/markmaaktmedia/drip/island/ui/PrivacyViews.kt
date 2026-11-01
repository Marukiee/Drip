package nl.markmaaktmedia.drip.island.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.island.PrivacyInfo
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** What can be in use, in the order the island shows them: the camera says the most, then the microphone. */
enum class PrivacyKind { CAMERA, MICROPHONE, LOCATION }

private val LocationBlue = Color(0xFF0A84FF)

fun PrivacyInfo.kinds(): List<PrivacyKind> = buildList {
    if (camera) add(PrivacyKind.CAMERA)
    if (microphone) add(PrivacyKind.MICROPHONE)
    if (location) add(PrivacyKind.LOCATION)
}

private fun PrivacyInfo.appOf(kind: PrivacyKind) = when (kind) {
    PrivacyKind.CAMERA -> cameraApp
    PrivacyKind.MICROPHONE -> microphoneApp
    PrivacyKind.LOCATION -> locationApp
}

/** Green for a camera, orange for a microphone, as Android itself shows them, and blue for location. */
fun inkOf(kind: PrivacyKind): Color = when (kind) {
    PrivacyKind.CAMERA -> IslandInk.Green
    PrivacyKind.MICROPHONE -> IslandInk.Orange
    PrivacyKind.LOCATION -> LocationBlue
}

fun privacyInk(privacy: PrivacyInfo): Color = privacy.kinds().firstOrNull()?.let(::inkOf) ?: IslandInk.Green

@Composable
private fun iconOf(kind: PrivacyKind): Painter = when (kind) {
    PrivacyKind.CAMERA -> DripIcons.Camera
    PrivacyKind.MICROPHONE -> DripIcons.MicFilled
    PrivacyKind.LOCATION -> DripIcons.Location
}

@Composable
private fun nameOf(kind: PrivacyKind): String = stringResource(
    when (kind) {
        PrivacyKind.CAMERA -> R.string.island_privacy_camera
        PrivacyKind.MICROPHONE -> R.string.island_privacy_microphone
        PrivacyKind.LOCATION -> R.string.island_privacy_location
    },
)

/** The name of an app, or what was given when it is not a package (the demo). */
private fun labelOf(context: Context, app: String): String = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(app, 0)).toString()
}.getOrDefault(app)

/** What was already greeted, so going back to the closed island from the card does not bring the icons in again. */
private val greeted = HashSet<PrivacyKind>()

/**
 * The icon of something in use, on a soft disc of its colour. Only when it starts being used does it fade and grow in,
 * calmly and without overshoot; coming back from the card it is simply there.
 */
@Composable
fun PrivacyGlyph(kind: PrivacyKind, size: Dp, modifier: Modifier = Modifier, animate: Boolean = true) {
    val ink = inkOf(kind)
    val fresh = remember { animate && greeted.add(kind) }
    val grow = remember { Animatable(if (fresh) 0f else 1f) }
    if (fresh) LaunchedEffect(Unit) { grow.animateTo(1f, spring(1f, 300f)) }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = 0.7f + 0.3f * grow.value
                scaleX = s; scaleY = s
                alpha = grow.value
            }
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconOf(kind), null, tint = ink, modifier = Modifier.size(size * 0.6f))
    }
}

/** Forgets the kinds no longer in use, so the next time one starts it is greeted again. */
fun forgetGreeted(inUse: List<PrivacyKind>) {
    greeted.retainAll(inUse.toSet())
}

/**
 * The closed island while a camera, microphone or location is in use: the most telling one at the start, the others
 * (a video call has both camera and microphone) at the end, or the app's own icon when there is only the one.
 */
@Composable
fun PrivacyCompact(privacy: PrivacyInfo, start: Modifier, end: Modifier, slot: Dp) {
    val kinds = privacy.kinds()
    val first = kinds.firstOrNull() ?: return
    AnimatedContent(first, start, transitionSpec = { swap() }, label = "privacyStart") { PrivacyGlyph(it, slot) }
    val rest = kinds.drop(1)
    val app = privacy.appOf(first)
    AnimatedContent(rest to app, end, transitionSpec = { swap() }, label = "privacyEnd") { (others, pkg) ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                others.size == 1 -> PrivacyGlyph(others[0], slot)
                others.size > 1 -> Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    others.forEach { PrivacyGlyph(it, slot * 0.62f) }
                }
                pkg != null -> AppIcon(null, pkg, slot * 0.8f)
            }
        }
    }
}

private fun swap() =
    (scaleIn(spring(0.9f, 450f), initialScale = 0.7f) + fadeIn(tween(160)))
        .togetherWith(scaleOut(tween(160), targetScale = 0.6f) + fadeOut(tween(140)))

/**
 * The opened card: one row per thing in use, with the app that started it. Android does not tell other apps which app
 * holds the camera, so it is the app that was in front when it started; a row opens that app's info page, where its
 * permissions are.
 */
@Composable
fun PrivacyCard(privacy: PrivacyInfo, onDone: () -> Unit) {
    val context = LocalContext.current
    val m = LocalCardMetrics.current
    val kinds = privacy.kinds()
    Text(
        stringResource(R.string.island_privacy_title), color = IslandInk.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        kinds.forEachIndexed { index, kind ->
            val app = privacy.appOf(kind)
            val last = index == kinds.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (index == 0) 20.dp else 4.dp, topEnd = if (index == 0) 20.dp else 4.dp,
                bottomStart = if (last) m.buttonOuterRadius else 4.dp, bottomEnd = if (last) m.buttonOuterRadius else 4.dp,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(shape)
                    .background(IslandInk.Surface)
                    .clickable(enabled = app != null) {
                        app?.let { openAppInfo(context, it) }
                        onDone()
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Still in the card: the icons only pop when something starts being used, not every time it opens.
                PrivacyGlyph(kind, 38.dp, animate = false)
                Column(Modifier.weight(1f)) {
                    Text(nameOf(kind), color = IslandInk.Primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Subtitle(app?.let { labelOf(context, it) } ?: stringResource(R.string.island_privacy_unknown_app))
                }
                if (app != null) AppIcon(null, app, 28.dp)
            }
        }
    }
    if (kinds.isEmpty()) Subtitle(stringResource(R.string.island_privacy_none), Modifier.padding(start = 4.dp))
}

private fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

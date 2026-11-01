package nl.markmaaktmedia.drip.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The palette used when the user turns wallpaper colours off, or on a phone that
 * cannot provide them.
 *
 * Built around one idea: a cool indigo that reads as "thinking", with a warm rose as
 * the only other voice in the room. Two hues and nothing else, so the accent means
 * something when it does show up. The tilted capsule in the launcher icon is drawn in
 * the same indigo, which is what ties the icon to the app it opens.
 */
object DripPalette {
    val Indigo10 = Color(0xFF0B0A2B)
    val Indigo20 = Color(0xFF1B1A4A)
    val Indigo30 = Color(0xFF2E2C6B)
    val Indigo40 = Color(0xFF454391)
    val Indigo50 = Color(0xFF5B5BD6)
    val Indigo70 = Color(0xFF9E9DF0)
    val Indigo80 = Color(0xFFBEBDF8)
    val Indigo90 = Color(0xFFE2E1FF)
    val Indigo95 = Color(0xFFF1F0FF)

    val Rose10 = Color(0xFF3A0720)
    val Rose20 = Color(0xFF561433)
    val Rose30 = Color(0xFF742C4B)
    val Rose40 = Color(0xFF934464)
    val Rose80 = Color(0xFFFFB0CB)
    val Rose90 = Color(0xFFFFD9E4)

    val Neutral0 = Color(0xFF000000)
    val Neutral6 = Color(0xFF0E0E13)
    val Neutral10 = Color(0xFF15141B)
    val Neutral12 = Color(0xFF1A1922)
    val Neutral17 = Color(0xFF232230)
    val Neutral22 = Color(0xFF2C2B3A)
    val Neutral24 = Color(0xFF302F3F)
    val Neutral80 = Color(0xFFC8C5D5)
    val Neutral90 = Color(0xFFE5E1F0)
    val Neutral95 = Color(0xFFF3F0FB)
    val Neutral98 = Color(0xFFFCFAFF)
    val Neutral100 = Color(0xFFFFFFFF)

    val Error40 = Color(0xFFBA1A1A)
    val Error80 = Color(0xFFFFB4AB)
    val Error90 = Color(0xFFFFDAD6)
    val Error10 = Color(0xFF410002)

    /** Reserved for the urgent badge, so nothing else is allowed to be this loud. */
    val Urgent = Color(0xFFE8613C)
    val UrgentContainerLight = Color(0xFFFFE3DA)
    val UrgentContainerDark = Color(0xFF5A2113)
}

val DripLightColors = lightColorScheme(
    primary = DripPalette.Indigo50,
    onPrimary = DripPalette.Neutral100,
    primaryContainer = DripPalette.Indigo90,
    onPrimaryContainer = DripPalette.Indigo20,
    secondary = DripPalette.Indigo40,
    onSecondary = DripPalette.Neutral100,
    secondaryContainer = DripPalette.Indigo95,
    onSecondaryContainer = DripPalette.Indigo30,
    tertiary = DripPalette.Rose40,
    onTertiary = DripPalette.Neutral100,
    tertiaryContainer = DripPalette.Rose90,
    onTertiaryContainer = DripPalette.Rose20,
    background = DripPalette.Neutral98,
    onBackground = DripPalette.Neutral10,
    surface = DripPalette.Neutral98,
    onSurface = DripPalette.Neutral10,
    surfaceVariant = DripPalette.Neutral95,
    onSurfaceVariant = Color(0xFF49475A),
    surfaceContainerLowest = DripPalette.Neutral100,
    surfaceContainerLow = Color(0xFFFAF7FF),
    surfaceContainer = Color(0xFFF4F1FC),
    surfaceContainerHigh = Color(0xFFEEEBF7),
    surfaceContainerHighest = Color(0xFFE8E5F2),
    outline = Color(0xFF7B7890),
    outlineVariant = Color(0xFFCBC7DA),
    error = DripPalette.Error40,
    onError = DripPalette.Neutral100,
    errorContainer = DripPalette.Error90,
    onErrorContainer = DripPalette.Error10,
    inverseSurface = DripPalette.Neutral17,
    inverseOnSurface = DripPalette.Neutral95,
    inversePrimary = DripPalette.Indigo80,
)

val DripDarkColors = darkColorScheme(
    primary = DripPalette.Indigo80,
    onPrimary = DripPalette.Indigo20,
    primaryContainer = DripPalette.Indigo30,
    onPrimaryContainer = DripPalette.Indigo90,
    secondary = DripPalette.Indigo70,
    onSecondary = DripPalette.Indigo20,
    secondaryContainer = DripPalette.Indigo30,
    onSecondaryContainer = DripPalette.Indigo90,
    tertiary = DripPalette.Rose80,
    onTertiary = DripPalette.Rose20,
    tertiaryContainer = DripPalette.Rose30,
    onTertiaryContainer = DripPalette.Rose90,
    background = DripPalette.Neutral10,
    onBackground = DripPalette.Neutral90,
    surface = DripPalette.Neutral10,
    onSurface = DripPalette.Neutral90,
    surfaceVariant = DripPalette.Neutral22,
    onSurfaceVariant = DripPalette.Neutral80,
    surfaceContainerLowest = DripPalette.Neutral6,
    surfaceContainerLow = DripPalette.Neutral12,
    surfaceContainer = DripPalette.Neutral17,
    surfaceContainerHigh = DripPalette.Neutral22,
    surfaceContainerHighest = DripPalette.Neutral24,
    outline = Color(0xFF908DA3),
    outlineVariant = Color(0xFF49475A),
    error = DripPalette.Error80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = DripPalette.Error90,
    inverseSurface = DripPalette.Neutral90,
    inverseOnSurface = DripPalette.Neutral17,
    inversePrimary = DripPalette.Indigo50,
)

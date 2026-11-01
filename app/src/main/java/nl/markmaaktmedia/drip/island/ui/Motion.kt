package nl.markmaaktmedia.drip.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/** Digits of one width ("tnum"), for anything that counts. */
val TabularNumbers = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

/**
 * Text whose characters roll when they change, like the wheels of a counter: only the digits that change move, up when
 * the number goes up and down when it goes down. For clocks, timers and percentages on the island.
 */
@Composable
fun RollingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.SemiBold,
    countingDown: Boolean = false,
) {
    Row(modifier) {
        // Keyed from the right, so "9:59" to "10:00" lines the units up instead of shifting every wheel.
        text.forEachIndexed { index, char ->
            key(text.length - index) {
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val up = if (countingDown) targetState < initialState else targetState > initialState
                    val dir = if (up) 1 else -1
                    (slideInVertically(spring(0.75f, 600f)) { it * dir } + fadeIn(tween(120)))
                        .togetherWith(slideOutVertically(spring(0.75f, 600f)) { -it * dir } + fadeOut(tween(90)))
                },
                label = "digit$index",
            ) { c ->
                // Every digit as wide as the others, so a running clock stands still and only its digits roll.
                Text(c.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, style = TabularNumbers)
            }
            }
        }
    }
}

package nl.markmaaktmedia.drip.island.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.drip.data.IslandSettings

/**
 * The top of this phone's screen in miniature, with the island drawn the way the settings make it. For the shape page,
 * so a slider can be watched while it moves. [expanded] shows the opened island instead of the closed one.
 */
@Composable
fun IslandPreview(settings: IslandSettings, modifier: Modifier = Modifier, expanded: Boolean = false) {
    Box(modifier.fillMaxWidth().height(if (expanded) 220.dp else 90.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.size(120.dp, 28.dp).background(Color.Black, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)))
    }
}

package nl.markmaaktmedia.drip.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.graph
import nl.markmaaktmedia.drip.island.Demo
import nl.markmaaktmedia.drip.island.Haptics
import nl.markmaaktmedia.drip.ui.components.ActionRow
import nl.markmaaktmedia.drip.ui.components.SectionHeader
import nl.markmaaktmedia.drip.ui.components.SettingsGroup
import nl.markmaaktmedia.drip.ui.components.SwitchRow
import nl.markmaaktmedia.drip.ui.theme.DripIcons

/** Motion and feel: how lively and how fast the island moves, and the taps it gives under the thumb. */
@Composable
internal fun MotionPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val (island, edit) = rememberIsland()

    SettingsPageFrame(stringResource(R.string.settings_cat_motion), onBack) {
        SectionHeader(stringResource(R.string.motion_section), top = 20.dp)
        SettingsGroup {
            SettingsTarget(FocusKeys.Bounce, 0, 3) {
                SliderRow(
                    0, 3, DripIcons.Swipe, stringResource(R.string.motion_bounce), stringResource(R.string.motion_bounce_sub),
                    island.bounce, 0f..1f, 0.05f, { percentLabel(it) },
                    onChange = { v -> edit { it.copy(bounce = v) } },
                )
            }
            SettingsTarget(FocusKeys.Speed, 1, 3) {
                SliderRow(
                    1, 3, DripIcons.Speed, stringResource(R.string.motion_speed), stringResource(R.string.motion_speed_sub),
                    island.speed, 0.5f..2f, 0.05f, { stringResource(R.string.value_speed, speedText(it)) },
                    onChange = { v -> edit { it.copy(speed = v) } },
                )
            }
            // Open and close again, so the bounce and the speed can be judged on the real thing.
            ActionRow(
                2, 3, DripIcons.OpenInFull, stringResource(R.string.motion_try), stringResource(R.string.motion_try_sub),
                { graph.hub.demo(Demo.EXPAND) },
            )
        }

        SectionHeader(stringResource(R.string.motion_section_haptics), top = 20.dp)
        SettingsGroup {
            SettingsTarget(FocusKeys.Haptics, 0, 3) {
                SwitchRow(
                    0, 3, DripIcons.Vibration, stringResource(R.string.motion_haptics), stringResource(R.string.motion_haptics_sub),
                    island.hapticsEnabled, { on -> edit { it.copy(hapticsEnabled = on) } },
                )
            }
            SettingsTarget(FocusKeys.Strength, 1, 3) {
                SliderRow(
                    1, 3, DripIcons.Vibration, stringResource(R.string.motion_strength), stringResource(R.string.motion_strength_sub),
                    island.hapticsStrength, 0.1f..1f, 0.05f, { percentLabel(it) },
                    onChange = { v ->
                        edit { it.copy(hapticsStrength = v) }
                        graph.haptics.strength = v
                    },
                )
            }
            val off = stringResource(R.string.motion_haptics_off)
            ActionRow(
                2, 3, DripIcons.Vibration, stringResource(R.string.motion_feel),
                if (island.hapticsEnabled) stringResource(R.string.motion_feel_sub) else off,
                {
                    if (!island.hapticsEnabled) {
                        Toast.makeText(context, off, Toast.LENGTH_SHORT).show()
                    } else {
                        // From the page itself, so it is felt with the strength shown even before the island has read it.
                        graph.haptics.enabled = true
                        graph.haptics.strength = island.hapticsStrength
                        graph.haptics.play(Haptics.Kind.EXPAND)
                    }
                },
                modifier = Modifier.alpha(if (island.hapticsEnabled) 1f else 0.6f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

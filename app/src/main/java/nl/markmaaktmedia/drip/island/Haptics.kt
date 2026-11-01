package nl.markmaaktmedia.drip.island

import android.content.Context
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The feel of the island. Each movement has its own short pattern built from the haptic primitives, so opening rises
 * under the thumb and closing falls away, the way the shape itself moves.
 *
 * Primitives need a vibrator that can play them, which most phones from the last few years have. Where one is missing
 * the plain predefined clicks stand in, so nothing goes silent.
 */
class Haptics(context: Context) {

    enum class Kind { EXPAND, COLLAPSE, ALERT, TAP, LONG_PRESS, SWIPE, TICK, CALL }

    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    @Volatile var enabled = true
    /** 0 to 1, scales every primitive. */
    @Volatile var strength = 0.7f

    private val primitives: Boolean = vibrator.hasVibrator() && vibrator.areAllPrimitivesSupported(
        Composition.PRIMITIVE_CLICK, Composition.PRIMITIVE_TICK, Composition.PRIMITIVE_LOW_TICK,
        Composition.PRIMITIVE_QUICK_RISE, Composition.PRIMITIVE_QUICK_FALL, Composition.PRIMITIVE_THUD,
    )

    fun play(kind: Kind) {
        if (!enabled || !vibrator.hasVibrator()) return
        val s = strength.coerceIn(0.05f, 1f)
        val effect = if (primitives) {
            val c = VibrationEffect.startComposition()
            when (kind) {
                // A swell that lands on a click as the shape settles.
                Kind.EXPAND -> c.addPrimitive(Composition.PRIMITIVE_QUICK_RISE, 0.45f * s)
                    .addPrimitive(Composition.PRIMITIVE_CLICK, 0.7f * s, 60)
                Kind.COLLAPSE -> c.addPrimitive(Composition.PRIMITIVE_QUICK_FALL, 0.5f * s)
                    .addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.5f * s, 40)
                Kind.ALERT -> c.addPrimitive(Composition.PRIMITIVE_TICK, 0.8f * s)
                    .addPrimitive(Composition.PRIMITIVE_CLICK, 0.5f * s, 70)
                Kind.TAP -> c.addPrimitive(Composition.PRIMITIVE_CLICK, 0.6f * s)
                Kind.LONG_PRESS -> c.addPrimitive(Composition.PRIMITIVE_THUD, 0.8f * s)
                Kind.SWIPE -> c.addPrimitive(Composition.PRIMITIVE_TICK, 0.7f * s)
                    .addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.5f * s, 30)
                Kind.TICK -> c.addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.6f * s)
                Kind.CALL -> c.addPrimitive(Composition.PRIMITIVE_THUD, 0.9f * s)
                    .addPrimitive(Composition.PRIMITIVE_CLICK, 0.6f * s, 90)
            }
            c.compose()
        } else {
            VibrationEffect.createPredefined(
                when (kind) {
                    Kind.EXPAND, Kind.LONG_PRESS, Kind.CALL -> VibrationEffect.EFFECT_HEAVY_CLICK
                    Kind.ALERT, Kind.TAP, Kind.SWIPE -> VibrationEffect.EFFECT_CLICK
                    Kind.COLLAPSE, Kind.TICK -> VibrationEffect.EFFECT_TICK
                },
            )
        }
        runCatching { vibrator.vibrate(effect) }
    }
}

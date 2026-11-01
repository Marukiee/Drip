package nl.markmaaktmedia.drip.automation

/**
 * What an automation app (Tasker, MacroDroid, adb) asked for, read from the extras of its broadcast. Plain Kotlin over a
 * map of values, so the tests can feed it anything a stranger might send. Bad input gives null, never an exception.
 */
const val ACTION_SHOW = "nl.markmaaktmedia.drip.SHOW"
const val ACTION_HIDE = "nl.markmaaktmedia.drip.HIDE"

/** Live items from automation live in the hub next to the notifications, told apart by this start of their key. */
internal const val AUTOMATION_PREFIX = "auto:"

internal fun automationKey(id: String) = AUTOMATION_PREFIX + id
internal fun isAutomationKey(key: String) = key.startsWith(AUTOMATION_PREFIX)

/** The names an automation may pick an icon by. The receiver maps them to Drip's Material Symbols. */
internal val automationIconNames = setOf(
    "timer", "music", "bolt", "notifications", "check", "warning", "home", "car", "alarm", "battery", "call", "mail",
    "message", "info", "calendar", "location", "download", "upload", "sports", "heart", "computer", "phone", "coffee",
    "wifi", "bluetooth", "lock", "headphones", "camera", "mic", "sleep",
)

sealed interface AutomationCommand {
    val id: String

    data class Show(
        override val id: String,
        val title: String,
        val text: String? = null,
        val icon: String? = null,
        /** ARGB. */
        val color: Int? = null,
        /** 0 to 1. */
        val progress: Float? = null,
        /** A countdown of this many seconds from now. */
        val seconds: Long? = null,
        /** How long a short announcement stays; null for the setting of the user. */
        val durationMs: Long? = null,
        /** A lasting item in the live list instead of a short announcement. */
        val lasting: Boolean = false,
        val packageName: String? = null,
    ) : AutomationCommand

    data class Hide(override val id: String) : AutomationCommand
}

private const val MAX_ID = 64
private const val MAX_TITLE = 80
private const val MAX_TEXT = 240
private const val MAX_SECONDS = 7L * 24 * 3600
private const val MIN_DURATION_MS = 1_000L
private const val MAX_DURATION_MS = 60_000L
private val colorPattern = Regex("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")
private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

// Tasker sends every extra as text and adb's --ei sends a number, so both are read as either.
private fun Any?.asText(): String? = when (this) {
    is CharSequence -> toString()
    is Number, is Boolean, is Char -> toString()
    else -> null
}?.trim()?.takeIf { it.isNotEmpty() }

private fun Any?.asNumber(): Double? = when (this) {
    is Number -> toDouble()
    is CharSequence -> toString().trim().replace(',', '.').toDoubleOrNull()
    else -> null
}?.takeIf { it.isFinite() }

internal fun parseColor(value: String?): Int? {
    val hex = value?.trim()?.let(colorPattern::matchEntire)?.groupValues?.get(1) ?: return null
    val argb = hex.toLong(16)
    return (if (hex.length == 6) argb or 0xFF000000L else argb).toInt()
}

internal fun parseAutomation(action: String?, extras: Map<String, Any?>): AutomationCommand? {
    val id = extras["id"].asText()?.takeIf { it.length <= MAX_ID } ?: return null
    return when (action) {
        ACTION_HIDE -> AutomationCommand.Hide(id)
        ACTION_SHOW -> {
            val title = extras["title"].asText()?.take(MAX_TITLE)
            val text = extras["text"].asText()?.take(MAX_TEXT)
            // Something to read is the least a thing on the island needs; a lone text becomes the title.
            val heading = title ?: text ?: return null
            AutomationCommand.Show(
                id = id,
                title = heading,
                text = text.takeIf { title != null },
                icon = extras["icon"].asText()?.lowercase()?.takeIf { it in automationIconNames },
                color = parseColor(extras["color"].asText()),
                progress = extras["progress"].asNumber()?.coerceIn(0.0, 100.0)?.div(100)?.toFloat(),
                seconds = extras["seconds"].asNumber()?.toLong()?.takeIf { it > 0 }?.coerceAtMost(MAX_SECONDS),
                durationMs = extras["duration"].asNumber()?.toLong()?.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS),
                lasting = extras["kind"].asText()?.lowercase() == "live",
                packageName = extras["package"].asText()?.takeIf { it.length <= 255 && packagePattern.matches(it) },
            )
        }
        else -> null
    }
}

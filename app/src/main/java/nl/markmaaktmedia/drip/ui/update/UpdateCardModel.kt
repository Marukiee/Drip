package nl.markmaaktmedia.drip.ui.update

import nl.markmaaktmedia.drip.update.ReleaseInfo
import nl.markmaaktmedia.drip.update.UpdateState
import org.json.JSONArray
import java.util.Locale

/** Which look the update card has. The card morphs between them, it never swaps. */
enum class UpdateCardPhase { Rest, Downloading, Installing, Failed }

internal fun updateCardPhase(state: UpdateState): UpdateCardPhase? = when (state) {
    is UpdateState.Available, is UpdateState.NeedsPermission -> UpdateCardPhase.Rest
    is UpdateState.Downloading -> UpdateCardPhase.Downloading
    is UpdateState.ReadyToInstall, is UpdateState.Installing, is UpdateState.AwaitingConfirmation -> UpdateCardPhase.Installing
    is UpdateState.Failed -> UpdateCardPhase.Failed
    else -> null
}

/** The release a state is about, or null when it is about none. */
internal val UpdateState.release: ReleaseInfo?
    get() = when (this) {
        is UpdateState.Available -> release
        is UpdateState.Downloading -> release
        is UpdateState.ReadyToInstall -> release
        is UpdateState.NeedsPermission -> release
        is UpdateState.Installing -> release
        is UpdateState.AwaitingConfirmation -> release
        is UpdateState.Failed -> release
        else -> null
    }

/** At most [max] short lines of what is new: the bullets of the summary when it has any, else its first lines. */
internal fun updateNotes(summary: String, max: Int = 2): List<String> {
    val lines = summary.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val bullets = lines.filter { it.startsWith("• ") }.map { it.removePrefix("• ").trim() }
    return (bullets.ifEmpty { lines.filterNot { it.startsWith("#") } }).take(max)
}

/**
 * What [version] says is new in changelog.json, per language ("en", "nl"): new things first, then what got better, then
 * what was fixed, [max] lines at most. Empty when the file does not know the version.
 */
internal fun changelogNotes(json: String, version: String, max: Int = 2): Map<String, List<String>> = runCatching {
    val array = JSONArray(json)
    val entry = (0 until array.length()).map { array.getJSONObject(it) }.firstOrNull { it.optString("version") == version }
        ?: return@runCatching emptyMap()
    listOf("en", "nl").mapNotNull { language ->
        val text = entry.optJSONObject(language) ?: return@mapNotNull null
        val lines = listOf("new", "better", "fixed").flatMap { key ->
            text.optJSONArray(key)?.let { list -> (0 until list.length()).map { list.getString(it) } }.orEmpty()
        }
        (language to lines.take(max)).takeIf { lines.isNotEmpty() }
    }.toMap()
}.getOrDefault(emptyMap())

/** The lines for [language], else the English ones. */
internal fun notesFor(notes: Map<String, List<String>>, language: String): List<String> =
    notes[language] ?: notes["en"].orEmpty()

/** Megabytes with one decimal, in the language of the phone. */
internal fun megabytes(bytes: Long, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.1f", bytes / 1_000_000.0)

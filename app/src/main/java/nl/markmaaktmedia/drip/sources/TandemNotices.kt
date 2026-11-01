package nl.markmaaktmedia.drip.sources

/**
 * Tandem, Drip's sister app, says what it does in notifications. These rules know its channels and its words, read from
 * Tandem's own code: `engine/Notifications.kt` (Channels), `engine/TandemService.kt` (the bar and the connection),
 * `files/ReadProgress.kt` and `quickshare/QuickShareNotifications.kt`. When Tandem renames one, the tests here say so.
 */
internal val tandemPackages = setOf("nl.markmaaktmedia.tandem", "nl.markmaaktmedia.tandem.debug")

internal fun isTandem(packageName: String) = packageName in tandemPackages

enum class TandemRole {
    /** A file going to or coming from another device: a bar that stays while it runs. */
    TRANSFER,
    /** The service notification that lists the devices that are connected. Never a card, only news when one joins. */
    CONNECTION,
    /** Something sent with Quick Share went on the clipboard. */
    CLIPBOARD,
}

internal const val TANDEM_CHANNEL_CONNECTION = "connection"
internal const val TANDEM_CHANNEL_TRANSFERS = "transfers"
internal const val TANDEM_CHANNEL_QUICKSHARE = "quickshare_progress"

/** R.string.quickshare_copied in English and Dutch, the languages Tandem speaks. */
private val copiedTexts = setOf("copied to the clipboard", "gekopieerd naar het klembord")

/** R.string.service_connected: "Connected to %1$s" and "Verbonden met %1$s", the names joined by ", ". */
private val connectedPrefixes = listOf("connected to ", "verbonden met ")

internal fun tandemRole(f: NoticeFacts): TandemRole? {
    if (!isTandem(f.packageName)) return null
    val bar = f.progressMax > 0 || f.indeterminate
    return when (f.channelId) {
        TANDEM_CHANNEL_CONNECTION -> TandemRole.CONNECTION
        TANDEM_CHANNEL_TRANSFERS -> TandemRole.TRANSFER.takeIf { f.ongoing && bar }
        // Quick Share's bar and its "received" note share one channel; only the bar is ongoing.
        TANDEM_CHANNEL_QUICKSHARE -> when {
            f.ongoing && bar -> TandemRole.TRANSFER
            !f.ongoing && f.text?.trim()?.lowercase() in copiedTexts -> TandemRole.CLIPBOARD
            else -> null
        }
        else -> null
    }
}

/** The devices the connection notification names, or none while Tandem is still looking. */
internal fun tandemConnectedTo(text: String?): List<String> {
    val t = text?.trim() ?: return emptyList()
    val prefix = connectedPrefixes.firstOrNull { t.lowercase().startsWith(it) } ?: return emptyList()
    return t.substring(prefix.length).split(", ").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/** Remembers who was connected, so only a device that joins is announced and not every refresh of the list. */
internal class TandemWatch {
    private val online = HashMap<String, Set<String>>()

    /** Records [names] for the notification [key] and gives the ones that are new. A seed is never news. */
    fun joined(key: String, names: List<String>, seed: Boolean): List<String> {
        val before = online.put(key, names.toSet())
        if (seed) return emptyList()
        return names.filter { before == null || it !in before }
    }

    fun forget(key: String) {
        online.remove(key)
    }

    fun clear() = online.clear()
}

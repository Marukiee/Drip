package nl.markmaaktmedia.drip.sources

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nl.markmaaktmedia.drip.island.IslandHub
import nl.markmaaktmedia.drip.island.LiveInfo

/**
 * Live entries Drip makes itself (an appointment, an install) carry keys that start with this. The notification listener
 * writes the same list and leaves these alone, so neither side wipes what the other put there.
 */
internal const val OWN_LIVE_PREFIX = "drip:"

/**
 * This list with every entry whose key starts with [prefix] replaced by [own]. An entry that stays keeps its place, so a
 * progress update does not make it jump ahead of the others; a new one goes in front, as the list is newest first.
 */
fun List<LiveInfo>.withOwn(prefix: String, own: List<LiveInfo>): List<LiveInfo> {
    val byKey = own.associateBy { it.key }
    val kept = mapNotNull { entry -> if (entry.key.startsWith(prefix)) byKey[entry.key] else entry }
    val placed = kept.mapTo(HashSet()) { it.key }
    return own.filter { it.key !in placed } + kept
}

internal fun IslandHub.putOwnLive(prefix: String, own: List<LiveInfo>) {
    live.update { it.withOwn(prefix, own) }
}

/**
 * Pinged by the settings screens when a runtime permission may have changed. The sources run in the same process, inside
 * the island service, and would otherwise only find out at their next timer, or never for a callback that could not be
 * registered without it.
 */
internal object SourceGrants {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun changed() {
        _version.update { it + 1 }
    }
}

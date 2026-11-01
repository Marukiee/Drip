package nl.markmaaktmedia.drip.engine

import android.content.Context
import android.content.Intent
import java.text.Collator
import java.text.Normalizer

/** An app on the home screen, for the pickers. The icon is loaded by the row that shows it. */
data class LauncherApp(val packageName: String, val label: String)

object LauncherApps {

    /** Every app with a launcher icon except Drip itself, by name. Slow on a phone with many apps: call it off the main thread. */
    fun load(context: Context): List<LauncherApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return pm.queryIntentActivities(launcher, 0)
            .asSequence()
            .map { it.activityInfo.packageName to it }
            .filter { (pkg, _) -> pkg != context.packageName }
            .distinctBy { it.first }
            .map { (pkg, info) -> LauncherApp(pkg, info.loadLabel(pm).toString().ifBlank { pkg }) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }

    /**
     * The apps that match every word of [query], in the name or in the package. A name that starts with the query comes
     * first, then a name with a word that starts with it, then the rest, each in the order they came in. Case and accents
     * do not count, so "cafe" finds "Café".
     */
    fun filter(apps: List<LauncherApp>, query: String): List<LauncherApp> {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return apps
        val whole = words.joinToString(" ")
        return apps.mapNotNull { app ->
            val label = fold(app.label)
            val pkg = app.packageName.lowercase()
            if (!words.all { label.contains(it) || pkg.contains(it) }) return@mapNotNull null
            val rank = when {
                label.startsWith(whole) -> 0
                words.all { word -> label.split(' ').any { it.startsWith(word) } } -> 1
                else -> 2
            }
            rank to app
        }.sortedBy { it.first }.map { it.second }
    }

    /** The chosen apps first, so they can be found again; the rest after them, each in their own order. */
    fun chosenFirst(apps: List<LauncherApp>, chosen: Set<String>): List<LauncherApp> =
        apps.filter { it.packageName in chosen } + apps.filter { it.packageName !in chosen }

    internal fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
}

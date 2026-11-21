package nl.markmaaktmedia.markupdate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.DrawableRes

/**
 * Wat per app anders is. Meer niet: de rest van markupdate is in elke app dezelfde code.
 *
 * ```
 * MarkUpdate.init(this, MarkUpdateConfig(
 *     owner = "Marukiee", repo = "Keeper", apkAsset = "Keeper.apk",
 *     currentVersion = BuildConfig.VERSION_NAME, appName = "Keeper", notificationIcon = R.drawable.ic_stat_keeper,
 * ))
 * ```
 */
data class MarkUpdateConfig @JvmOverloads constructor(
    /** GitHub-eigenaar en -repo waar de releases staan. */
    val owner: String,
    val repo: String,
    /** Naam van het appbestand bij de release, bijvoorbeeld "Keeper.apk". De controlesom staat in "<naam>.sha256". */
    val apkAsset: String,
    /** De versie van de draaiende app, bijvoorbeeld `BuildConfig.VERSION_NAME`. Een achtervoegsel "-debug" telt niet mee. */
    val currentVersion: String,
    /** Voor de User-Agent en de tekst van de melding. */
    val appName: String,
    /** Het kleine icoon van de melding "klaar om te installeren" (een eenkleurig statusbalkicoon). */
    @param:DrawableRes val notificationIcon: Int,
    /** Hetzelfde kanaal als de app al had, zodat de instelling van de gebruiker blijft. */
    val notificationChannelId: String = "update",
    /** Bij het openen van de app wordt hooguit zo vaak gecontroleerd: elke controle wekt de radio. */
    val checkIntervalMs: Long = 6 * 60 * 60 * 1000L,
)

/**
 * Het ene exemplaar van de updater van dit proces. Zet het op in `Application.onCreate`: de ontvanger van de installer (die Android ook
 * wakker kan maken als de app niet draait) en de kaart gebruiken hetzelfde exemplaar.
 */
object MarkUpdate {
    @Volatile private var instance: MarkUpdater? = null

    /** Maakt de updater aan en zet het meldingskanaal klaar. Nog eens aanroepen geeft hetzelfde exemplaar terug. */
    @JvmStatic
    @Synchronized
    fun init(context: Context, config: MarkUpdateConfig): MarkUpdater {
        instance?.let { return it }
        val app = context.applicationContext
        createChannel(app, config.notificationChannelId)
        return MarkUpdater(app, config, UpdateStore(app)).also { instance = it }
    }

    /** De updater; [init] moet eerst gedraaid hebben. */
    @JvmStatic
    fun get(): MarkUpdater = instance ?: error("MarkUpdate.init(...) is nog niet aangeroepen, zet het in Application.onCreate")

    @JvmStatic
    fun getOrNull(): MarkUpdater? = instance

    private fun createChannel(context: Context, id: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(id, context.getString(R.string.markupdate_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.markupdate_channel_description) },
        )
    }
}

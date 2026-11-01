package nl.markmaaktmedia.drip.sources

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
import nl.markmaaktmedia.drip.R
import nl.markmaaktmedia.drip.data.IslandSettings
import nl.markmaaktmedia.drip.island.ActionKind
import nl.markmaaktmedia.drip.island.Alert
import nl.markmaaktmedia.drip.island.AlertKind
import nl.markmaaktmedia.drip.island.CallInfo
import nl.markmaaktmedia.drip.island.IslandAction
import nl.markmaaktmedia.drip.island.LiveInfo

/** Turns a StatusBarNotification into what the island shows. [cancel] removes a notification by its key. */
internal class NotificationReader(
    private val context: Context,
    private val apps: Apps,
    private val cancel: (String) -> Unit,
) {

    fun facts(sbn: StatusBarNotification): NoticeFacts {
        val n = sbn.notification
        val e = n.extras
        val promoted = Build.VERSION.SDK_INT >= 36 && n.flags and FLAG_PROMOTED_ONGOING != 0
        val template = e.getString(Notification.EXTRA_TEMPLATE)?.substringAfterLast('$')?.substringAfterLast('.')
        var progress = e.getInt(Notification.EXTRA_PROGRESS)
        var max = e.getInt(Notification.EXTRA_PROGRESS_MAX)
        var indeterminate = e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        if (Build.VERSION.SDK_INT >= 36 && template == "ProgressStyle" && max <= 0) {
            // Android 16's progress style keeps its length in segments, which only the style itself adds up.
            safely("progress style") {
                (Notification.Builder.recoverBuilder(context, n).style as? Notification.ProgressStyle)?.let {
                    progress = it.progress
                    max = it.progressMax
                    indeterminate = it.isProgressIndeterminate
                }
            }
        }
        return NoticeFacts(
            packageName = sbn.packageName,
            category = n.category,
            template = template,
            ongoing = promoted || n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            hasMediaSession = e.containsKey(Notification.EXTRA_MEDIA_SESSION),
            callType = if (e.containsKey(EXTRA_CALL_TYPE)) e.getInt(EXTRA_CALL_TYPE) else null,
            hasCallIntents = CALL_INTENTS.any(e::containsKey),
            hasFullScreenIntent = n.fullScreenIntent != null,
            showsChronometer = e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
            countDown = e.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
            progress = progress,
            progressMax = max,
            indeterminate = indeterminate,
            promoted = promoted,
            title = e.text(Notification.EXTRA_TITLE) ?: e.text(Notification.EXTRA_TITLE_BIG),
            text = e.text(Notification.EXTRA_TEXT) ?: e.text(Notification.EXTRA_BIG_TEXT),
            subText = e.text(Notification.EXTRA_SUB_TEXT),
            channelId = n.channelId,
        )
    }

    /** What the notification says. A post with the same fingerprint is a silent refresh, not news. */
    fun fingerprint(sbn: StatusBarNotification): String {
        val e = sbn.notification.extras
        val last = messages(e).lastOrNull()
        return listOf(
            e.text(Notification.EXTRA_TITLE), e.text(Notification.EXTRA_TEXT), e.text(Notification.EXTRA_BIG_TEXT),
            last?.text?.toString(), last?.timestamp?.toString(),
        ).joinToString("\u0001") { it.orEmpty() }
    }

    fun call(sbn: StatusBarNotification, f: NoticeFacts): CallInfo {
        val n = sbn.notification
        val e = n.extras
        val person = BundleCompat.getParcelable(e, Notification.EXTRA_CALL_PERSON, Person::class.java)
        val incoming = isIncomingCall(f)
        val answer = e.pending(Notification.EXTRA_ANSWER_INTENT)
        val decline = e.pending(Notification.EXTRA_DECLINE_INTENT)
        val hangUp = e.pending(Notification.EXTRA_HANG_UP_INTENT)
        val ownIntents = listOfNotNull(answer, decline, hangUp)
        val ownKinds = buildSet {
            if (answer != null) add(ActionKind.ANSWER)
            if (decline != null) add(ActionKind.DECLINE)
            if (hangUp != null) add(ActionKind.HANG_UP)
        }
        // The app's own buttons (speaker, mute) next to the ones of the call style, without doubling any of them.
        val extra = n.actions.orEmpty()
            .filter { it.remoteInputs.isNullOrEmpty() && it.actionIntent != null && it.actionIntent !in ownIntents }
            .map { action(it, inCall = true) }
            .filter { it.kind !in ownKinds }
        val actions = buildList {
            decline?.let { add(press(R.string.src_call_decline, ActionKind.DECLINE, it)) }
            addAll(extra)
            answer?.let { add(press(R.string.src_call_answer, ActionKind.ANSWER, it)) }
            hangUp?.let { add(press(R.string.src_call_hang_up, ActionKind.HANG_UP, it)) }
        }
        val label = apps.label(sbn.packageName)
        return CallInfo(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = label,
            appIcon = apps.icon(sbn.packageName),
            caller = person?.name?.toString()?.takeIf { it.isNotBlank() } ?: f.title
                ?: e.text(Notification.EXTRA_CONVERSATION_TITLE) ?: label,
            callerPhoto = person?.icon?.toBitmapWithin(context, PHOTO_PX) ?: n.getLargeIcon()?.toBitmapWithin(context, PHOTO_PX),
            status = f.text ?: context.getString(if (incoming) R.string.src_call_incoming else R.string.src_call_ongoing),
            incoming = incoming,
            startedAt = n.`when`.takeIf { !incoming && f.showsChronometer && !f.countDown && it > 0 },
            actions = actions,
            open = opener(sbn),
        )
    }

    fun live(sbn: StatusBarNotification, f: NoticeFacts): LiveInfo {
        val n = sbn.notification
        val label = apps.label(sbn.packageName)
        val critical = if (Build.VERSION.SDK_INT >= 36) safely("short critical text") { n.shortCriticalText } else null
        return LiveInfo(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = label,
            appIcon = apps.icon(sbn.packageName),
            accent = n.color.takeIf { it != Notification.COLOR_DEFAULT },
            kind = liveKindFor(f),
            title = f.title ?: label,
            text = f.text,
            progress = liveProgress(f),
            indeterminate = f.indeterminate,
            chronometerBase = n.`when`.takeIf { f.showsChronometer && it > 0 },
            countDown = f.countDown,
            shortText = shortTextFor(f, critical),
            actions = buttons(n),
            open = opener(sbn),
        )
    }

    fun alert(sbn: StatusBarNotification, f: NoticeFacts, kind: AlertKind, s: IslandSettings): Alert {
        val n = sbn.notification
        val e = n.extras
        val last = messages(e).lastOrNull()
        val title = e.text(Notification.EXTRA_CONVERSATION_TITLE) ?: f.title ?: apps.label(sbn.packageName)
        val text = if (!s.notifyShowText) {
            null
        } else {
            val said = last?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val sender = last?.senderPerson?.name?.toString()?.takeIf { it.isNotBlank() }
            when {
                said == null -> f.text
                sender != null && e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) -> "$sender: $said"
                else -> said
            }
        }
        val people = BundleCompat.getParcelableArrayList(e, Notification.EXTRA_PEOPLE_LIST, Person::class.java)
        val image = n.getLargeIcon()?.toBitmapWithin(context, PHOTO_PX)
            ?: last?.senderPerson?.icon?.toBitmapWithin(context, PHOTO_PX)
            ?: people?.firstNotNullOfOrNull { it.icon }?.toBitmapWithin(context, PHOTO_PX)
            ?: apps.icon(sbn.packageName)
        return Alert(
            id = sbn.key,
            kind = kind,
            title = title,
            text = text,
            iconRes = if (kind == AlertKind.ALARM) R.drawable.sym_alarm else R.drawable.sym_notifications_active,
            image = image,
            packageName = sbn.packageName,
            accent = n.color.takeIf { it != Notification.COLOR_DEFAULT },
            durationMs = s.alertDurationMs,
            actions = buttons(n),
            open = opener(sbn),
        )
    }

    /** Up to three buttons the island can press for the app. A reply needs a keyboard, so those stay in the shade. */
    private fun buttons(n: Notification): List<IslandAction> = n.actions.orEmpty()
        .filter { it.remoteInputs.isNullOrEmpty() && it.actionIntent != null }
        .take(MAX_ACTIONS)
        .map { action(it, inCall = false) }

    private fun action(a: Notification.Action, inCall: Boolean): IslandAction {
        val title = a.title?.toString().orEmpty()
        val kind = actionKindFor(a.semanticAction, title, !a.remoteInputs.isNullOrEmpty(), inCall)
        val intent = a.actionIntent
        // Drip draws its own icon for the kinds it knows, so only an unknown button needs the app's.
        val icon = if (kind == ActionKind.OTHER) safely("action icon") { apps.glyph(a.getIcon()) } else null
        return IslandAction(title, kind, icon) { intent?.sendFrom(context) }
    }

    private fun press(title: Int, kind: ActionKind, intent: PendingIntent) =
        IslandAction(context.getString(title), kind) { intent.sendFrom(context) }

    private fun opener(sbn: StatusBarNotification): () -> Unit {
        val n = sbn.notification
        val key = sbn.key
        val packageName = sbn.packageName
        return {
            val sent = n.contentIntent?.sendFrom(context) == true
            if (!sent) context.launchApp(packageName)
            if (n.flags and Notification.FLAG_AUTO_CANCEL != 0) cancel(key)
        }
    }

    private fun messages(e: Bundle): List<Notification.MessagingStyle.Message> = safely("messages") {
        val array = BundleCompat.getParcelableArray(e, Notification.EXTRA_MESSAGES, Bundle::class.java)
        if (array.isNullOrEmpty()) emptyList() else Notification.MessagingStyle.Message.getMessagesFromBundleArray(array)
    } ?: emptyList()

    private fun Bundle.text(key: String): String? = getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun Bundle.pending(key: String): PendingIntent? = BundleCompat.getParcelable(this, key, PendingIntent::class.java)

    private companion object {
        /** Notification.EXTRA_CALL_TYPE: the call style writes it since Android 12, the constant is public from 14. */
        const val EXTRA_CALL_TYPE = "android.callType"
        /** Notification.FLAG_PROMOTED_ONGOING, which only exists from Android 16. */
        const val FLAG_PROMOTED_ONGOING = 0x00040000
        const val PHOTO_PX = 192
        const val MAX_ACTIONS = 3
        val CALL_INTENTS = listOf(
            Notification.EXTRA_ANSWER_INTENT, Notification.EXTRA_DECLINE_INTENT, Notification.EXTRA_HANG_UP_INTENT,
        )
    }
}

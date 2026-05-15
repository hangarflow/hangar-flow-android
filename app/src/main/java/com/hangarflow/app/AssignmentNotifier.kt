package com.hangarflow.app

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.hangarflow.app.data.model.HFWorkLog

/**
 * Watches the SharedStore's work-log list and fires a local notification
 * when the current user is newly assigned to a log. Kicks in whenever a
 * `pullSnapshot` completes — uses the `hf_alerts` channel registered by
 * PushNotifications.
 */
object AssignmentNotifier {
    @Volatile private var appContext: Context? = null

    // IDs of work logs this user has *already been notified about*. Keeps
    // a reassign from spamming the same notification every sync tick.
    private val notified = mutableSetOf<String>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onSnapshot(currentUserId: String?, workLogs: List<HFWorkLog>) {
        val ctx = appContext ?: return
        if (currentUserId.isNullOrBlank()) return

        val assignedToMe = workLogs.filter { it.assignedUserId == currentUserId }
        for (log in assignedToMe) {
            if (notified.add(log.id)) notify(ctx, log)
        }
        // Drop IDs that no longer target us so a *future* reassign still
        // alerts. (E.g. admin yanked then re-assigned.)
        val assignedIds = assignedToMe.map { it.id }.toSet()
        notified.retainAll(assignedIds)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, log: HFWorkLog) {
        PushNotifications.ensureRegistered(context)
        val title = "New work log assigned"
        val body = buildString {
            append(log.title.ifBlank { "Untitled work log" })
            val tail = log.planeTailNumber
            if (tail.isNotBlank()) append(" · $tail")
        }
        val builder = NotificationCompat.Builder(context, PushNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // Use the log's stable ID so a second-sync duplicate overwrites
        // instead of stacking.
        val id = log.id.hashCode()
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { mgr.notify(id, builder.build()) }
    }
}

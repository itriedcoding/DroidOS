package dev.droidos.shell.shell.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.ServiceLocator

/** Bridges real system notifications into [NotificationCenter]. */
class DroidNotificationListener : NotificationListenerService() {

    private val center: NotificationCenter
        get() = ServiceLocator.notificationCenter

    override fun onCreate() {
        super.onCreate()
        center.attach(this)
        AppLog.i("NotificationListener", "Service created")
    }

    override fun onDestroy() {
        center.detach(this)
        center.setConnected(false)
        AppLog.i("NotificationListener", "Service destroyed")
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        center.setConnected(true)
        AppLog.i("NotificationListener", "Listener connected")
        refresh()
    }

    override fun onListenerDisconnected() {
        center.setConnected(false)
        AppLog.w("NotificationListener", "Listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    private fun refresh() {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            AppLog.w("NotificationListener", "Could not read active notifications", e)
            return
        }
        val items = active?.mapNotNull { it.toItem() } ?: emptyList()
        center.publish(items)
    }

    private fun StatusBarNotification.toItem(): NotificationCenter.Item? {
        val notification = notification ?: return null
        val extras = notification.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        // Skip empty/system-internal notifications that would render as blank rows.
        if (title.isBlank() && text.isBlank()) return null

        val appLabel = ServiceLocator.apps.labelFor(packageName)
        return NotificationCenter.Item(
            key = key,
            packageName = packageName,
            appLabel = appLabel,
            title = title,
            text = text,
            postedAt = postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            ongoing = isOngoing,
            clearable = isClearable
        )
    }
}

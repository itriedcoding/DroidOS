package dev.droidos.shell.shell.notifications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.droidos.shell.core.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live notification state shared between the NotificationListenerService and the shade.
 * Dismissal goes through the real listener so the notification leaves the system shade too.
 */
class NotificationCenter(private val context: Context) {

    data class Item(
        val key: String,
        val packageName: String,
        val appLabel: String,
        val title: String,
        val text: String,
        val postedAt: Long,
        val ongoing: Boolean,
        val clearable: Boolean,
        val actions: List<Action> = emptyList()
    )

    /** A button the posting app attached to its notification, executed through its own PendingIntent. */
    data class Action(
        val label: String,
        val pendingIntent: PendingIntent
    )

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var listener: DroidNotificationListener? = null

    internal fun attach(listener: DroidNotificationListener) {
        this.listener = listener
    }

    internal fun detach(listener: DroidNotificationListener) {
        if (this.listener === listener) this.listener = null
    }

    internal fun setConnected(connected: Boolean) {
        _connected.value = connected
        if (!connected) _items.value = emptyList()
    }

    internal fun publish(items: List<Item>) {
        _items.value = items.sortedByDescending { it.postedAt }
    }

    /** Runs a notification's own action, so the posting app receives the tap it expects. */
    fun fire(action: Action) {
        try {
            action.pendingIntent.send()
            AppLog.i("NotificationCenter", "Ran notification action ${action.label}")
        } catch (e: PendingIntent.CanceledException) {
            AppLog.w("NotificationCenter", "Action ${action.label} was cancelled by its app", e)
        } catch (e: Exception) {
            AppLog.e("NotificationCenter", "Could not run action ${action.label}", e)
        }
    }

    fun dismiss(key: String) {
        val active = listener
        if (active == null) {
            AppLog.w("NotificationCenter", "Dismiss ignored - listener not bound")
            return
        }
        try {
            active.cancelNotification(key)
            _items.value = _items.value.filterNot { it.key == key }
        } catch (e: Exception) {
            AppLog.e("NotificationCenter", "Could not cancel notification $key", e)
        }
    }

    fun dismissAll() {
        val active = listener ?: return
        try {
            active.cancelAllNotifications()
            _items.value = emptyList()
        } catch (e: Exception) {
            AppLog.e("NotificationCenter", "Could not cancel notifications", e)
        }
    }

    fun hasAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val component = ComponentName(context, DroidNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

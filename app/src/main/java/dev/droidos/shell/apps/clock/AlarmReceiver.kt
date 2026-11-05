package dev.droidos.shell.apps.clock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.droidos.shell.MainActivity
import dev.droidos.shell.core.AppLog

/**
 * Receives the platform delivery of an alarm or timer. It plays the device's real alarm tone,
 * posts a notification with a working Stop action, and re-arms repeating alarms for their next
 * occurrence. Nothing here is simulated: if the platform does not deliver, nothing rings.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> stopRinging()
            else -> fire(context, intent)
        }
    }

    private fun fire(context: Context, intent: Intent) {
        val isTimer = intent.getBooleanExtra(EXTRA_TIMER, false)
        val label = intent.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() }
            ?: if (isTimer) "Timer finished" else "Alarm"

        AppLog.i("AlarmReceiver", "Firing '$label' (timer=$isTimer)")
        playAlarmTone(context)
        notify(context, label, isTimer)

        if (!isTimer) {
            val days = intent.getIntArrayExtra(EXTRA_DAYS)?.toSet().orEmpty()
            if (days.isNotEmpty()) {
                val alarm = Alarm(
                    id = intent.getIntExtra(EXTRA_ID, 0),
                    hour = intent.getIntExtra(EXTRA_HOUR, 0),
                    minute = intent.getIntExtra(EXTRA_MINUTE, 0),
                    label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                    days = days,
                    enabled = true
                )
                val result = ClockRepository(context).schedule(alarm)
                if (result is dev.droidos.shell.core.Outcome.Err) {
                    AppLog.w("AlarmReceiver", "Could not re-arm alarm: ${result.message}")
                }
            }
        }
    }

    private fun notify(context: Context, label: String, isTimer: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            AppLog.w("AlarmReceiver", "Notifications are disabled - ringing without a notification")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarms and timers",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ringing alarms and finished timers"
                setSound(null, null) // the alarm tone is played directly, not twice
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getBroadcast(
            context,
            STOP_REQUEST,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText(if (isTimer) "Your timer has finished" else "Alarm ringing")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Stop", stop)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            AppLog.w("AlarmReceiver", "Notification permission missing", e)
        }
    }

    private fun playAlarmTone(context: Context) {
        try {
            stopRinging()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            active = RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (e: Exception) {
            AppLog.e("AlarmReceiver", "Could not play the alarm tone", e)
        }
    }

    companion object {
        const val ACTION_FIRE = "dev.droidos.shell.clock.FIRE"
        const val ACTION_STOP = "dev.droidos.shell.clock.STOP"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_LABEL = "alarm_label"
        const val EXTRA_HOUR = "alarm_hour"
        const val EXTRA_MINUTE = "alarm_minute"
        const val EXTRA_DAYS = "alarm_days"
        const val EXTRA_TIMER = "alarm_is_timer"

        private const val CHANNEL_ID = "droidos_alarms"
        private const val NOTIFICATION_ID = 4101
        private const val STOP_REQUEST = 4102

        private var active: Ringtone? = null

        private fun stopRinging() {
            try {
                active?.stop()
            } catch (e: Exception) {
                AppLog.w("AlarmReceiver", "Could not stop the ringtone", e)
            }
            active = null
        }
    }
}

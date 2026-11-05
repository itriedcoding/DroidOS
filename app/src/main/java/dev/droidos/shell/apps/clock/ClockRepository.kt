package dev.droidos.shell.apps.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dev.droidos.shell.MainActivity
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.attempt
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/** One scheduled alarm. Days use [Calendar] weekday constants; an empty set means "once". */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val days: Set<Int>,
    val enabled: Boolean
) {
    fun timeLabel(): String = "%02d:%02d".format(hour, minute)

    fun repeatLabel(): String = when {
        days.isEmpty() -> "Once"
        days.size == 7 -> "Every day"
        days == setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> "Weekends"
        days == setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
        ) -> "Weekdays"
        else -> days.sorted().joinToString(" ") { DAY_LETTERS[it] ?: "?" }
    }

    companion object {
        val DAY_LETTERS = mapOf(
            Calendar.MONDAY to "M",
            Calendar.TUESDAY to "T",
            Calendar.WEDNESDAY to "W",
            Calendar.THURSDAY to "T",
            Calendar.FRIDAY to "F",
            Calendar.SATURDAY to "S",
            Calendar.SUNDAY to "S"
        )
    }
}

/** Pure next-occurrence maths, kept out of the repository so it can be unit-tested. */
object AlarmSchedule {

    fun nextTrigger(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        days: Set<Int>,
        zone: TimeZone = TimeZone.getDefault()
    ): Long {
        if (days.isEmpty()) {
            val cal = Calendar.getInstance(zone).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= nowMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }

        for (offset in 0..7) {
            val candidate = Calendar.getInstance(zone).apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis <= nowMillis) continue
            if (days.contains(candidate.get(Calendar.DAY_OF_WEEK))) return candidate.timeInMillis
        }
        // Unreachable for a non-empty day set, but never hand back a time in the past.
        return nowMillis + 24L * 60L * 60L * 1000L
    }
}

/**
 * Stores alarms and hands them to [AlarmManager] as clock alarms, so they survive the process
 * being killed and are treated by the platform as user-visible alarms (they show in the status
 * bar and can ring through Do Not Disturb, subject to the device's alarm policy).
 */
class ClockRepository(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun alarms(): List<Alarm> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val days = mutableSetOf<Int>()
                obj.optJSONArray("days")?.let { dayArray ->
                    for (i in 0 until dayArray.length()) days += dayArray.optInt(i)
                }
                Alarm(
                    id = obj.optInt("id"),
                    hour = obj.optInt("hour"),
                    minute = obj.optInt("minute"),
                    label = obj.optString("label"),
                    days = days,
                    enabled = obj.optBoolean("enabled", true)
                )
            }.sortedWith(compareBy({ it.hour }, { it.minute }))
        } catch (e: Exception) {
            AppLog.e("ClockRepository", "Alarm store is corrupt", e)
            emptyList()
        }
    }

    fun save(alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(
                JSONObject().apply {
                    put("id", alarm.id)
                    put("hour", alarm.hour)
                    put("minute", alarm.minute)
                    put("label", alarm.label)
                    put("days", JSONArray(alarm.days.sorted()))
                    put("enabled", alarm.enabled)
                }
            )
        }
        prefs.edit().putString(KEY_ALARMS, array.toString()).apply()
    }

    fun upsert(alarm: Alarm) {
        val next = alarms().filterNot { it.id == alarm.id } + alarm
        save(next)
        if (alarm.enabled) schedule(alarm) else cancel(alarm.id)
    }

    fun delete(id: Int) {
        save(alarms().filterNot { it.id == id })
        cancel(id)
    }

    fun canScheduleExact(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    } catch (e: Exception) {
        false
    }

    fun exactAlarmSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun schedule(alarm: Alarm): Outcome<Unit> {
        val manager = alarmManager ?: return Outcome.Err("This device has no alarm service")
        if (!canScheduleExact()) {
            return Outcome.Err("Allow exact alarms for DroidOS, then re-enable this alarm")
        }
        val trigger = AlarmSchedule.nextTrigger(
            nowMillis = System.currentTimeMillis(),
            hour = alarm.hour,
            minute = alarm.minute,
            days = alarm.days
        )
        return attempt("Scheduling alarm ${alarm.id}") {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(trigger, showIntent()),
                fireIntent(alarm)
            )
            AppLog.i("ClockRepository", "Alarm ${alarm.id} set for $trigger")
        }
    }

    fun cancel(id: Int) {
        val manager = alarmManager ?: return
        try {
            manager.cancel(fireIntentById(id))
            AppLog.i("ClockRepository", "Alarm $id cancelled")
        } catch (e: Exception) {
            AppLog.w("ClockRepository", "Could not cancel alarm $id", e)
        }
    }

    fun scheduleTimer(triggerAtMillis: Long, label: String): Outcome<Unit> {
        val manager = alarmManager ?: return Outcome.Err("This device has no alarm service")
        if (!canScheduleExact()) {
            return Outcome.Err("Allow exact alarms for DroidOS, then start the timer again")
        }
        return attempt("Scheduling timer") {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                putExtra(AlarmReceiver.EXTRA_LABEL, label)
                putExtra(AlarmReceiver.EXTRA_TIMER, true)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                TIMER_REQUEST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
            AppLog.i("ClockRepository", "Timer set for $triggerAtMillis")
        }
    }

    fun cancelTimer() {
        val manager = alarmManager ?: return
        try {
            manager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    TIMER_REQUEST,
                    Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } catch (e: Exception) {
            AppLog.w("ClockRepository", "Could not cancel timer", e)
        }
    }

    private fun fireIntent(alarm: Alarm) = PendingIntent.getBroadcast(
        context,
        alarm.id,
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_LABEL, alarm.label)
            putExtra(AlarmReceiver.EXTRA_HOUR, alarm.hour)
            putExtra(AlarmReceiver.EXTRA_MINUTE, alarm.minute)
            putExtra(AlarmReceiver.EXTRA_DAYS, alarm.days.toIntArray())
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun fireIntentById(id: Int) = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ID, id)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val PREFS = "droidos_clock"
        private const val KEY_ALARMS = "alarms"
        private const val TIMER_REQUEST = 9001
    }
}

package dev.droidos.shell.apps.habits

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** Habits and their daily check-ins, stored in this app's private SQLite database. */
class HabitRepository(context: Context) {

    data class Habit(val id: Long, val name: String, val createdAt: String, val days: Set<LocalDate>)

    private val helper = object : SQLiteOpenHelper(context.applicationContext, NAME, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE habits (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "created_at TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE checkins (" +
                    "habit_id INTEGER NOT NULL, " +
                    "day TEXT NOT NULL, " +
                    "PRIMARY KEY (habit_id, day))"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    fun habits(): List<Habit> {
        val days = mutableMapOf<Long, MutableSet<LocalDate>>()
        helper.readableDatabase.rawQuery("SELECT habit_id, day FROM checkins", null).use { cursor ->
            while (cursor.moveToNext()) {
                val habitId = cursor.getLong(0)
                val date = HabitEngine.parseKey(cursor.getString(1)) ?: continue
                days.getOrPut(habitId) { mutableSetOf() }.add(date)
            }
        }
        val result = mutableListOf<Habit>()
        helper.readableDatabase.rawQuery(
            "SELECT id, name, created_at FROM habits ORDER BY id ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                result += Habit(
                    id = id,
                    name = cursor.getString(1) ?: "Habit",
                    createdAt = cursor.getString(2) ?: "",
                    days = days[id] ?: emptySet()
                )
            }
        }
        return result
    }

    fun add(name: String): Long {
        val values = ContentValues().apply {
            put("name", HabitEngine.normaliseName(name))
            put("created_at", LocalDate.now().toString())
        }
        return helper.writableDatabase.insert("habits", null, values)
    }

    fun delete(habitId: Long) {
        helper.writableDatabase.delete("habits", "id = ?", arrayOf(habitId.toString()))
        helper.writableDatabase.delete("checkins", "habit_id = ?", arrayOf(habitId.toString()))
    }

    fun isChecked(habitId: Long, day: LocalDate): Boolean =
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM checkins WHERE habit_id = ? AND day = ? LIMIT 1",
            arrayOf(habitId.toString(), HabitEngine.dateKey(day))
        ).use { it.moveToFirst() }

    /** Returns true when the day is marked after the toggle. */
    fun toggle(habitId: Long, day: LocalDate): Boolean {
        val key = HabitEngine.dateKey(day)
        if (isChecked(habitId, day)) {
            helper.writableDatabase.delete(
                "checkins",
                "habit_id = ? AND day = ?",
                arrayOf(habitId.toString(), key)
            )
            return false
        }
        val values = ContentValues().apply {
            put("habit_id", habitId)
            put("day", key)
        }
        helper.writableDatabase.insertWithOnConflict(
            "checkins",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        return true
    }

    fun close() {
        helper.close()
    }

    private companion object {
        const val NAME = "droidos-habits.db"
    }
}

package dev.droidos.shell.apps.flashcards

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** One collection of cards with their SM-2 state, stored in this app's private database. */
class FlashcardRepository(context: Context) {

    data class Card(
        val id: Long,
        val question: String,
        val answer: String,
        val easeFactor: Double,
        val intervalDays: Int,
        val repetitions: Int,
        val dueDate: LocalDate
    )

    data class Stats(val total: Int, val due: Int, val reviews: Int)

    private val helper = object : SQLiteOpenHelper(context.applicationContext, NAME, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE cards (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "question TEXT NOT NULL, " +
                    "answer TEXT NOT NULL, " +
                    "ease REAL NOT NULL DEFAULT 2.5, " +
                    "interval_days INTEGER NOT NULL DEFAULT 0, " +
                    "repetitions INTEGER NOT NULL DEFAULT 0, " +
                    "due TEXT NOT NULL)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    fun add(question: String, answer: String, today: LocalDate = LocalDate.now()): Long {
        val values = ContentValues().apply {
            put("question", question.trim())
            put("answer", answer.trim())
            put("ease", 2.5)
            put("interval_days", 0)
            put("repetitions", 0)
            put("due", today.toString())
        }
        return helper.writableDatabase.insert("cards", null, values)
    }

    fun due(today: LocalDate = LocalDate.now(), limit: Int = 50): List<Card> {
        val cards = mutableListOf<Card>()
        helper.readableDatabase.rawQuery(
            "SELECT id, question, answer, ease, interval_days, repetitions, due " +
                "FROM cards WHERE due <= ? ORDER BY due ASC, id ASC LIMIT ?",
            arrayOf(today.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) cards += read(cursor)
        }
        return cards
    }

    fun all(): List<Card> {
        val cards = mutableListOf<Card>()
        helper.readableDatabase.rawQuery(
            "SELECT id, question, answer, ease, interval_days, repetitions, due " +
                "FROM cards ORDER BY id ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) cards += read(cursor)
        }
        return cards
    }

    fun review(cardId: Long, state: SrsEngine.CardState, quality: Int, today: LocalDate): SrsEngine.Review {
        val result = SrsEngine.review(state, quality, today)
        val values = ContentValues().apply {
            put("ease", result.state.easeFactor)
            put("interval_days", result.state.intervalDays)
            put("repetitions", result.state.repetitions)
            put("due", result.dueDate.toString())
        }
        helper.writableDatabase.update("cards", values, "id = ?", arrayOf(cardId.toString()))
        return result
    }

    fun delete(cardId: Long) {
        helper.writableDatabase.delete("cards", "id = ?", arrayOf(cardId.toString()))
    }

    fun stats(today: LocalDate = LocalDate.now()): Stats {
        val total = helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM cards", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val due = helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM cards WHERE due <= ?",
            arrayOf(today.toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val reviews = helper.readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(repetitions), 0) FROM cards",
            null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return Stats(total = total, due = due, reviews = reviews)
    }

    fun close() {
        helper.close()
    }

    private fun read(cursor: android.database.Cursor): Card = Card(
        id = cursor.getLong(0),
        question = cursor.getString(1) ?: "",
        answer = cursor.getString(2) ?: "",
        easeFactor = cursor.getDouble(3),
        intervalDays = cursor.getInt(4),
        repetitions = cursor.getInt(5),
        dueDate = runCatching { LocalDate.parse(cursor.getString(6)) }.getOrDefault(LocalDate.now())
    )

    private companion object {
        const val NAME = "droidos-flashcards.db"
    }
}

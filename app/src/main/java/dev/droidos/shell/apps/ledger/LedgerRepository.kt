package dev.droidos.shell.apps.ledger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.droidos.shell.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class Entry(
    val id: Long = 0,
    val cents: Long,
    val category: String,
    val note: String,
    val income: Boolean,
    val createdAt: Long
)

data class Summary(val incomeCents: Long, val expenseCents: Long) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

/**
 * Amounts are held as integer cents, never floats, so totals cannot accumulate rounding error.
 * Parsing and formatting are pure and unit-tested.
 */
object Money {

    private val AMOUNT = Regex("^(-?)(\\d+)(?:\\.(\\d{1,2}))?$")

    fun parseToCents(input: String): Long? {
        val cleaned = input.trim().replace(",", "").removePrefix("+")
        if (cleaned.isEmpty()) return null
        val match = AMOUNT.matchEntire(cleaned) ?: return null
        val sign = if (match.groupValues[1] == "-") -1L else 1L
        val whole = match.groupValues[2].toLongOrNull() ?: return null
        val fraction = match.groupValues[3].padEnd(2, '0').ifEmpty { "0" }.toLongOrNull() ?: 0L
        return sign * (whole * 100L + fraction)
    }

    fun format(cents: Long): String {
        val negative = cents < 0
        val absolute = if (negative) -cents else cents
        val whole = String.format(Locale.US, "%,d", absolute / 100)
        val fraction = (absolute % 100).toString().padStart(2, '0')
        return (if (negative) "-" else "") + whole + "." + fraction
    }
}

object LedgerMath {
    fun summarise(entries: List<Entry>): Summary = Summary(
        incomeCents = entries.filter { it.income }.sumOf { it.cents },
        expenseCents = entries.filterNot { it.income }.sumOf { it.cents }
    )
}

class LedgerRepository(context: Context) {

    private val helper = LedgerDatabase(context.applicationContext)

    suspend fun all(): List<Entry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<Entry>()
        helper.readableDatabase.rawQuery(
            "SELECT id, cents, category, note, income, created_at FROM entries ORDER BY created_at DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries += Entry(
                    id = cursor.getLong(0),
                    cents = cursor.getLong(1),
                    category = cursor.getString(2),
                    note = cursor.getString(3),
                    income = cursor.getInt(4) == 1,
                    createdAt = cursor.getLong(5)
                )
            }
        }
        entries
    }

    suspend fun add(entry: Entry): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("cents", entry.cents)
            put("category", entry.category)
            put("note", entry.note)
            put("income", if (entry.income) 1 else 0)
            put("created_at", System.currentTimeMillis())
        }
        val id = helper.writableDatabase.insertOrThrow("entries", null, values)
        AppLog.i("Ledger", "Recorded entry $id")
        id
    }

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("entries", "id = ?", arrayOf(id.toString())) > 0
    }
}

private class LedgerDatabase(context: Context) :
    SQLiteOpenHelper(context, "droidos_ledger.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cents INTEGER NOT NULL,
                category TEXT NOT NULL,
                note TEXT NOT NULL,
                income INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_entries_created ON entries(created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the initial schema.
    }
}

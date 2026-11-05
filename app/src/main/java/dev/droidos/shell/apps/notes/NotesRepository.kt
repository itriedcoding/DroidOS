package dev.droidos.shell.apps.notes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.droidos.shell.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Note(
    val id: Long = 0,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val pinned: Boolean
)

/**
 * Notes are stored in a real SQLite database with parameterised statements; every write
 * is a transaction. Search is a prefix/substring match over title and body.
 */
class NotesRepository(context: Context) {

    private val helper = NotesDatabase(context.applicationContext)

    suspend fun all(query: String = ""): List<Note> = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val notes = mutableListOf<Note>()
        val sql = buildString {
            append("SELECT id, title, body, updated_at, pinned FROM notes")
            if (query.isNotBlank()) append(" WHERE title LIKE ? OR body LIKE ?")
            append(" ORDER BY pinned DESC, updated_at DESC")
        }
        val args = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                notes += Note(
                    id = cursor.getLong(0),
                    title = cursor.getString(1),
                    body = cursor.getString(2),
                    updatedAt = cursor.getLong(3),
                    pinned = cursor.getInt(4) == 1
                )
            }
        }
        notes
    }

    suspend fun save(note: Note): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("title", note.title)
            put("body", note.body)
            put("updated_at", System.currentTimeMillis())
            put("pinned", if (note.pinned) 1 else 0)
        }
        db.beginTransaction()
        try {
            val id = if (note.id == 0L) {
                db.insertOrThrow("notes", null, values)
            } else {
                db.update("notes", values, "id = ?", arrayOf(note.id.toString()))
                note.id
            }
            db.setTransactionSuccessful()
            AppLog.i("Notes", "Saved note $id")
            id
        } finally {
            db.endTransaction()
        }
    }

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        val deleted = helper.writableDatabase.delete("notes", "id = ?", arrayOf(id.toString()))
        AppLog.i("Notes", "Deleted note $id ($deleted row)")
        deleted > 0
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("pinned", if (pinned) 1 else 0) }
        helper.writableDatabase.update("notes", values, "id = ?", arrayOf(id.toString()))
        Unit
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM notes", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}

private class NotesDatabase(context: Context) :
    SQLiteOpenHelper(context, "droidos_notes.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_notes_updated ON notes(updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the initial schema; future migrations go here.
    }
}

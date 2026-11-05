package dev.droidos.shell.apps.vault

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.OpenableColumns
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.core.attempt
import dev.droidos.shell.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VaultItem(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val kind: Kind
) {
    enum class Kind { TEXT, FILE }
}

/**
 * Encrypted vault. Every payload is sealed with the AES-256-GCM Keystore key into
 * filesDir/vault; only opaque metadata (display name, size, timestamp) is stored in SQLite.
 * Without the Keystore key the blobs are unreadable, and destroying the key shreds access.
 */
class VaultRepository(
    private val context: Context,
    private val crypto: CryptoManager = ServiceLocator.crypto
) {

    private val database = VaultDatabase(context.applicationContext)
    private val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }

    suspend fun items(): List<VaultItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VaultItem>()
        database.readableDatabase.rawQuery(
            "SELECT id, name, size, created_at, kind FROM vault ORDER BY created_at DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += VaultItem(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    sizeBytes = cursor.getLong(2),
                    createdAt = cursor.getLong(3),
                    kind = if (cursor.getString(4) == "TEXT") VaultItem.Kind.TEXT else VaultItem.Kind.FILE
                )
            }
        }
        result
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        vaultDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun addText(name: String, text: String): Outcome<Long> = withContext(Dispatchers.IO) {
        attempt("Add vault text") {
            val id = insertRow(name, text.toByteArray().size.toLong(), VaultItem.Kind.TEXT)
            val blob = blobFor(id)
            val plain = File(context.cacheDir, "vault-plain-$id.txt")
            try {
                plain.writeText(text)
                val sealed = crypto.encryptFile(plain, blob)
                if (sealed is Outcome.Err) {
                    deleteRow(id)
                    blob.delete()
                    throw IllegalStateException(sealed.message)
                }
            } finally {
                plain.delete()
            }
            AppLog.i("Vault", "Stored text item $id")
            id
        }
    }

    suspend fun addFile(uri: Uri): Outcome<Long> = withContext(Dispatchers.IO) {
        attempt("Add file to vault") {
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: "vault-${System.currentTimeMillis()}"
            val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
                ?: 0L

            val id = insertRow(name, size, VaultItem.Kind.FILE)
            val blob = blobFor(id)
            val temp = File(context.cacheDir, "vault-import-$id.bin")
            try {
                resolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("The provider returned no data stream")
                val sealed = crypto.encryptFile(temp, blob)
                if (sealed is Outcome.Err) {
                    deleteRow(id)
                    blob.delete()
                    throw IllegalStateException(sealed.message)
                }
            } finally {
                temp.delete()
            }
            AppLog.i("Vault", "Stored file item $id ($name, $size bytes)")
            id
        }
    }

    suspend fun readText(item: VaultItem): Outcome<String> = withContext(Dispatchers.IO) {
        attempt("Read vault text") {
            val plain = File(context.cacheDir, "vault-read-${item.id}.txt")
            try {
                when (val result = crypto.decryptFile(blobFor(item.id), plain)) {
                    is Outcome.Err -> throw IllegalStateException(result.message)
                    is Outcome.Ok -> plain.readText()
                }
            } finally {
                plain.delete()
            }
        }
    }

    suspend fun export(item: VaultItem, target: Uri): Outcome<Unit> = withContext(Dispatchers.IO) {
        attempt("Export vault item") {
            val plain = File(context.cacheDir, "vault-export-${item.id}.bin")
            try {
                when (val result = crypto.decryptFile(blobFor(item.id), plain)) {
                    is Outcome.Err -> throw IllegalStateException(result.message)
                    is Outcome.Ok -> Unit
                }
                context.contentResolver.openOutputStream(target)?.use { output ->
                    plain.inputStream().use { it.copyTo(output) }
                } ?: throw IllegalStateException("Could not open the destination file")
            } finally {
                plain.delete()
            }
            AppLog.i("Vault", "Exported item ${item.id}")
        }
    }

    suspend fun delete(item: VaultItem): Outcome<Unit> = withContext(Dispatchers.IO) {
        attempt("Delete vault item") {
            blobFor(item.id).delete()
            deleteRow(item.id)
            AppLog.i("Vault", "Deleted vault item ${item.id}")
        }
    }

    private fun blobFor(id: Long): File = File(vaultDir, "$id.blob")

    private fun insertRow(name: String, size: Long, kind: VaultItem.Kind): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("size", size)
            put("created_at", System.currentTimeMillis())
            put("kind", kind.name)
        }
        return database.writableDatabase.insertOrThrow("vault", null, values)
    }

    private fun deleteRow(id: Long) {
        database.writableDatabase.delete("vault", "id = ?", arrayOf(id.toString()))
    }
}

private class VaultDatabase(context: Context) :
    SQLiteOpenHelper(context, "droidos_vault.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE vault (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                size INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                kind TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

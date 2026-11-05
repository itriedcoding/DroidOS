package dev.droidos.shell.apps.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.attempt

data class FileItem(
    val documentId: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?
)

/**
 * File access through the Storage Access Framework: the user grants one folder, and every
 * list/create/rename/delete/open operation is a real DocumentsContract call against it.
 * No permissions are taken that the user did not explicitly grant.
 */
class FilesRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("droidos_files", Context.MODE_PRIVATE)

    fun savedTreeUri(): Uri? = prefs.getString(KEY_TREE, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun saveTreeUri(uri: Uri) {
        // Persist the read/write grant across process restarts.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { AppLog.w("Files", "Could not persist tree permission", it) }
        prefs.edit().putString(KEY_TREE, uri.toString()).apply()
        AppLog.i("Files", "Storage root set to $uri")
    }

    fun clearTreeUri() {
        savedTreeUri()?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs.edit().remove(KEY_TREE).apply()
    }

    fun pickFolderIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )

    fun rootDocumentId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    fun list(treeUri: Uri, documentId: String): Outcome<List<FileItem>> =
        attempt("List documents") {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val items = mutableListOf<FileItem>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(2)
                    items += FileItem(
                        documentId = cursor.getString(0),
                        name = cursor.getString(1) ?: "(unnamed)",
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        size = cursor.getLong(3),
                        lastModified = cursor.getLong(4),
                        mimeType = mime
                    )
                }
            }
            items.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
        }

    fun createFolder(treeUri: Uri, parentDocumentId: String, name: String): Outcome<Uri> =
        attempt("Create folder $name") {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
            DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name
            ) ?: throw IllegalStateException("The provider refused to create the folder")
        }

    fun rename(treeUri: Uri, documentId: String, newName: String): Outcome<Unit> =
        attempt("Rename document") {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                ?: throw IllegalStateException("The provider refused to rename this item")
            Unit
        }

    fun delete(treeUri: Uri, documentId: String): Outcome<Unit> = attempt("Delete document") {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) {
            throw IllegalStateException("The provider refused to delete this item")
        }
        Unit
    }

    fun open(treeUri: Uri, item: FileItem): Outcome<Unit> = attempt("Open ${item.name}") {
        if (item.isDirectory) throw IllegalArgumentException("Folders cannot be opened externally")
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, item.documentId)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, item.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private companion object {
        const val KEY_TREE = "tree_uri"
    }
}

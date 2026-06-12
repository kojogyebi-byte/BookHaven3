package com.bookhaven.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.bookhaven.reader.format.EpubParser
import com.bookhaven.reader.format.MobiParser
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Owns the on-device library: imports files into private storage, extracts
 * metadata + cover art, and persists the catalog as JSON.
 */
class BookRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val catalogFile = File(context.filesDir, "library.json")

    fun load(): List<Book> {
        if (!catalogFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<Book>>(catalogFile.readText())
        }.getOrDefault(emptyList())
            .filter { File(it.filePath).exists() }
            .sortedByDescending { maxOf(it.lastOpenedMillis, it.dateAddedMillis) }
    }

    private fun persist(books: List<Book>) {
        catalogFile.writeText(json.encodeToString(books))
    }

    fun updateBook(books: List<Book>, updated: Book): List<Book> {
        val next = books.map { if (it.id == updated.id) updated else it }
        persist(next)
        return next
    }

    fun deleteBook(books: List<Book>, target: Book): List<Book> {
        File(target.filePath).parentFile?.deleteRecursively()
        val next = books.filterNot { it.id == target.id }
        persist(next)
        return next
    }

    /**
     * Copies the picked document into private storage and builds a [Book].
     * Returns null if the file could not be read.
     */
    fun importFromUri(uri: Uri, existing: List<Book>): List<Book> {
        val displayName = queryDisplayName(uri) ?: "Untitled"
        val format = BookFormat.fromExtension(displayName)
        val id = UUID.randomUUID().toString()
        val dir = File(booksDir, id).apply { mkdirs() }
        val ext = displayName.substringAfterLast('.', "dat")
        val dest = File(dir, "source.$ext")

        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            } ?: return existing
        }.isSuccess
        if (!ok || dest.length() == 0L) {
            dir.deleteRecursively()
            return existing
        }

        val meta = extractMetadata(dest, format, dir, displayName)
        val book = Book(
            id = id,
            title = meta.title,
            author = meta.author,
            format = format,
            filePath = dest.absolutePath,
            coverPath = meta.coverPath
        )
        val next = listOf(book) + existing
        persist(next)
        return next
    }

    private data class Meta(val title: String, val author: String, val coverPath: String?)

    private fun extractMetadata(file: File, format: BookFormat, dir: File, displayName: String): Meta {
        val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "Untitled" }
        return runCatching {
            when (format) {
                BookFormat.EPUB -> {
                    val info = EpubParser.readMetadata(file)
                    val cover = info.coverBytes?.let { saveCover(dir, it, info.coverExt) }
                    Meta(info.title ?: fallbackTitle, info.author ?: "Unknown Author", cover)
                }
                BookFormat.MOBI, BookFormat.AZW -> {
                    val info = MobiParser.readMetadata(file)
                    Meta(info.title ?: fallbackTitle, info.author ?: "Unknown Author", null)
                }
                BookFormat.PDF -> {
                    val cover = renderPdfCover(file, dir)
                    Meta(fallbackTitle, "PDF Document", cover)
                }
                else -> Meta(fallbackTitle, format.label, null)
            }
        }.getOrDefault(Meta(fallbackTitle, "Unknown Author", null))
    }

    private fun saveCover(dir: File, bytes: ByteArray, ext: String): String? = runCatching {
        val f = File(dir, "cover.${ext.ifBlank { "img" }}")
        f.writeBytes(bytes)
        f.absolutePath
    }.getOrNull()

    private fun renderPdfCover(file: File, dir: File): String? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val scale = 2
                    val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = File(dir, "cover.png")
                    FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    out.absolutePath
                }
            }
        }
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return DocumentFile.fromSingleUri(context, uri)?.name
    }
}

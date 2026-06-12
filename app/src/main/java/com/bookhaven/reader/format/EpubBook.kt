package com.bookhaven.reader.format

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Runtime EPUB reader: exposes the spine (ordered chapters) and resolves
 * in-archive resources (images / CSS) so a WebView can render each chapter.
 */
class EpubBook private constructor(
    private val zip: ZipFile,
    val chapterPaths: List<String>,
    val tocTitles: List<String>
) : Closeable {

    val chapterCount: Int get() = chapterPaths.size

    /** Raw XHTML of a chapter, decoded as UTF-8. */
    @Synchronized
    fun chapterHtml(index: Int): String {
        val path = chapterPaths.getOrNull(index) ?: return "<html><body></body></html>"
        val entry = zip.getEntry(path) ?: return "<html><body></body></html>"
        return zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
    }

    /** Base URL used so relative resources resolve against the chapter's folder. */
    fun chapterBaseUrl(index: Int): String {
        val path = chapterPaths.getOrNull(index) ?: return BASE
        val dir = path.substringBeforeLast('/', "")
        return if (dir.isEmpty()) BASE else "$BASE$dir/"
    }

    /** Serve a resource requested by the WebView (returns null if not in the archive). */
    @Synchronized
    fun openResource(requestUrl: String): Pair<InputStream, String>? {
        if (!requestUrl.startsWith(BASE)) return null
        val raw = requestUrl.removePrefix(BASE).substringBefore('?').substringBefore('#')
        val path = java.net.URLDecoder.decode(raw, "UTF-8")
        val entry = zip.getEntry(path) ?: return null
        val bytes = zip.getInputStream(entry).readBytes()
        return ByteArrayInputStream(bytes) to mimeFor(path)
    }

    override fun close() { runCatching { zip.close() } }

    companion object {
        const val BASE = "https://book.local/"

        fun open(file: File): EpubBook {
            val zip = ZipFile(file)
            val opfPath = zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf") }?.name
                ?: throw IllegalStateException("No OPF found")
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = zip.getInputStream(zip.getEntry(opfPath)).bufferedReader().readText()

            // manifest id -> href
            val hrefById = HashMap<String, String>()
            Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).forEach { tag ->
                val id = Regex("id=\"([^\"]+)\"").find(tag.value)?.groupValues?.get(1)
                val href = Regex("href=\"([^\"]+)\"").find(tag.value)?.groupValues?.get(1)
                if (id != null && href != null) hrefById[id] = href
            }
            // spine order
            val spine = Regex("<itemref\\b[^>]*idref=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .findAll(opf).map { it.groupValues[1] }.toList()

            val chapterPaths = spine.mapNotNull { id ->
                hrefById[id]?.let { EpubParser.resolve(opfDir, it) }
            }.filter { zip.getEntry(it) != null }

            val finalPaths = chapterPaths.ifEmpty {
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".xhtml") || it.endsWith(".html") || it.endsWith(".htm") }
                    .sorted().toList()
            }
            val titles = finalPaths.map { it.substringAfterLast('/').substringBeforeLast('.') }
            return EpubBook(zip, finalPaths, titles)
        }

        private fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "css" -> "text/css"
            "js" -> "text/javascript"
            "xhtml", "html", "htm" -> "text/html"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
    }
}

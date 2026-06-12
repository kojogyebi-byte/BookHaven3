package com.bookhaven.reader.format

import android.util.Xml
import java.io.File
import java.util.zip.ZipFile

data class EpubMetadata(
    val title: String?,
    val author: String?,
    val coverBytes: ByteArray?,
    val coverExt: String
)

/** Lightweight EPUB (OPF) metadata reader. An EPUB is a ZIP of XHTML + an OPF manifest. */
object EpubParser {

    fun readMetadata(file: File): EpubMetadata {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip) ?: return EpubMetadata(null, null, null, "img")
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = zip.getEntry(opfPath)?.let { zip.getInputStream(it).bufferedReader().readText() }
                ?: return EpubMetadata(null, null, null, "img")

            val parsed = parseOpf(opf)
            val coverHref = parsed.coverHref?.let { resolve(opfDir, it) }
            var coverBytes: ByteArray? = null
            var coverExt = "img"
            if (coverHref != null) {
                zip.getEntry(coverHref)?.let { entry ->
                    coverBytes = zip.getInputStream(entry).readBytes()
                    coverExt = coverHref.substringAfterLast('.', "img")
                }
            }
            return EpubMetadata(parsed.title, parsed.author, coverBytes, coverExt)
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val container = zip.getEntry("META-INF/container.xml") ?: return guessOpf(zip)
        val text = zip.getInputStream(container).bufferedReader().readText()
        val m = Regex("full-path=\"([^\"]+\\.opf)\"").find(text)
        return m?.groupValues?.get(1) ?: guessOpf(zip)
    }

    private fun guessOpf(zip: ZipFile): String? =
        zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf") }?.name

    data class ParsedOpf(val title: String?, val author: String?, val coverHref: String?)

    private fun parseOpf(xml: String): ParsedOpf {
        var title: String? = null
        var author: String? = null
        var coverMetaId: String? = null
        // manifest: id -> href, id -> properties
        val hrefById = HashMap<String, String>()
        val propsById = HashMap<String, String>()

        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        var text = StringBuilder()
        var inTitle = false
        var inCreator = false

        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    when (parser.name.substringAfter(':')) {
                        "title" -> { inTitle = true; text = StringBuilder() }
                        "creator" -> { inCreator = true; text = StringBuilder() }
                        "meta" -> {
                            if (parser.getAttributeValue(null, "name") == "cover") {
                                coverMetaId = parser.getAttributeValue(null, "content")
                            }
                        }
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            val props = parser.getAttributeValue(null, "properties") ?: ""
                            if (id.isNotEmpty()) { hrefById[id] = href; propsById[id] = props }
                        }
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> text.append(parser.text)
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    when (parser.name.substringAfter(':')) {
                        "title" -> { if (inTitle && title == null) title = text.toString().trim(); inTitle = false }
                        "creator" -> { if (inCreator && author == null) author = text.toString().trim(); inCreator = false }
                    }
                }
            }
            event = parser.next()
        }

        val coverHref = when {
            coverMetaId != null && hrefById.containsKey(coverMetaId) -> hrefById[coverMetaId]
            else -> propsById.entries.firstOrNull { it.value.contains("cover-image") }?.let { hrefById[it.key] }
        }
        return ParsedOpf(title?.ifBlank { null }, author?.ifBlank { null }, coverHref)
    }

    fun resolve(dir: String, href: String): String {
        val clean = href.substringBefore('#')
        if (dir.isEmpty()) return clean
        // normalize ../ segments
        val parts = ("$dir/$clean").split('/').toMutableList()
        val stack = ArrayList<String>()
        for (p in parts) {
            when (p) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(p)
            }
        }
        return stack.joinToString("/")
    }
}

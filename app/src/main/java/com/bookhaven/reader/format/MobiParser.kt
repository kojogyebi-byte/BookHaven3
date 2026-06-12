package com.bookhaven.reader.format

import java.io.File

data class MobiMetadata(val title: String?, val author: String?)

class MobiDrmException(msg: String) : Exception(msg)

/**
 * Minimal Mobipocket reader. Parses the PDB record table, the PalmDOC/MOBI
 * header, decompresses PalmDOC (LZ77) text records, and strips HTML to text.
 * DRM-encrypted books are detected and rejected (they require Amazon keys).
 */
object MobiParser {

    fun readMetadata(file: File): MobiMetadata {
        val bytes = file.readBytes()
        val info = parseHeader(bytes)
        return MobiMetadata(info.title, info.author)
    }

    /** Returns reflowable plain text extracted from the book. */
    fun readText(file: File): String {
        val bytes = file.readBytes()
        val info = parseHeader(bytes)
        if (info.encrypted) throw MobiDrmException("This book is DRM-protected and cannot be opened.")
        val sb = StringBuilder()
        for (i in 1..info.textRecordCount) {
            val recIndex = info.firstContentRecord + (i - 1)
            if (recIndex >= info.recordOffsets.size) break
            val start = info.recordOffsets[recIndex]
            val end = if (recIndex + 1 < info.recordOffsets.size) info.recordOffsets[recIndex + 1] else bytes.size
            if (start !in 0 until bytes.size || end <= start) continue
            val raw = bytes.copyOfRange(start, end)
            val decoded = if (info.compression == 2) palmDocDecompress(raw) else raw
            sb.append(String(decoded, charset(info.charset)))
        }
        return htmlToText(sb.toString())
    }

    private data class Header(
        val recordOffsets: IntArray,
        val compression: Int,
        val textRecordCount: Int,
        val firstContentRecord: Int,
        val encrypted: Boolean,
        val charset: String,
        val title: String?,
        val author: String?
    )

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
        ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun parseHeader(b: ByteArray): Header {
        val numRecords = u16(b, 76)
        val offsets = IntArray(numRecords) { u32(b, 78 + it * 8) }

        val rec0 = offsets.getOrElse(0) { 0 }
        val compression = u16(b, rec0 + 0)
        val textRecordCount = u16(b, rec0 + 8)
        val encType = u16(b, rec0 + 12) // 0 none, 1 old mobipocket, 2 Mobipocket DRM
        val encrypted = encType != 0

        // MOBI header begins at rec0 + 16 with identifier "MOBI"
        var charset = "windows-1252"
        var firstContent = 1
        var title: String? = null
        var author: String? = null
        val mobiStart = rec0 + 16
        if (mobiStart + 4 <= b.size && b[mobiStart] == 'M'.code.toByte() && b[mobiStart + 1] == 'O'.code.toByte()) {
            val encoding = u32(b, mobiStart + 12)
            charset = if (encoding == 65001) "UTF-8" else "windows-1252"
            val firstNonBook = u32(b, mobiStart + 80)
            if (firstNonBook in 1..numRecords) firstContent = 1
            // EXTH metadata
            val exthFlags = u32(b, mobiStart + 0x80)
            val fullTitleOffset = u32(b, mobiStart + 0x54)
            val fullTitleLen = u32(b, mobiStart + 0x58)
            if ((exthFlags and 0x40) != 0) {
                val headerLen = u32(b, mobiStart + 4)
                val exthStart = mobiStart + 16 + headerLen
                val pair = parseExth(b, exthStart)
                title = pair.first; author = pair.second
            }
            if (title == null && fullTitleLen in 1..1024) {
                val to = rec0 + fullTitleOffset
                if (to + fullTitleLen <= b.size) {
                    title = String(b, to, fullTitleLen, charset(charset)).trim()
                }
            }
        }
        return Header(offsets, compression, textRecordCount, firstContent, encrypted, charset, title, author)
    }

    /** EXTH: returns (title, author) where present. */
    private fun parseExth(b: ByteArray, start: Int): Pair<String?, String?> {
        if (start + 12 > b.size) return null to null
        if (!(b[start] == 'E'.code.toByte() && b[start + 1] == 'X'.code.toByte())) return null to null
        val count = u32(b, start + 8)
        var p = start + 12
        var title: String? = null
        var author: String? = null
        repeat(count) {
            if (p + 8 > b.size) return@repeat
            val type = u32(b, p)
            val len = u32(b, p + 4)
            if (len < 8 || p + len > b.size) return@repeat
            val data = String(b, p + 8, len - 8, Charsets.UTF_8).trim()
            when (type) {
                100 -> if (author == null) author = data   // creator
                503 -> title = data                        // updated title
            }
            p += len
        }
        return title to author
    }

    private fun palmDocDecompress(data: ByteArray): ByteArray {
        val out = ArrayList<Byte>(data.size * 2)
        var i = 0
        while (i < data.size) {
            val c = data[i].toInt() and 0xFF
            i++
            when {
                c == 0 -> out.add(0)
                c in 1..8 -> { var k = 0; while (k < c && i < data.size) { out.add(data[i]); i++; k++ } }
                c in 9..0x7F -> out.add(c.toByte())
                c in 0x80..0xBF -> {
                    if (i >= data.size) break
                    val c2 = data[i].toInt() and 0xFF; i++
                    val pair = (c shl 8) or c2
                    val distance = (pair and 0x3FFF) shr 3
                    val length = (c2 and 0x07) + 3
                    if (distance in 1..out.size) {
                        repeat(length) { out.add(out[out.size - distance]) }
                    }
                }
                else -> { out.add(0x20); out.add((c xor 0x80).toByte()) }
            }
        }
        return out.toByteArray()
    }

    private fun htmlToText(html: String): String {
        return html
            .replace(Regex("(?is)<head.*?</head>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?i)<\\s*br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)<p[^>]*>"), "")
            .replace(Regex("(?i)<h[1-6][^>]*>"), "\n\n")
            .replace(Regex("(?i)</h[1-6]>"), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}

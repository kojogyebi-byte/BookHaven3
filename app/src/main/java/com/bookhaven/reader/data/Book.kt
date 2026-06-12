package com.bookhaven.reader.data

import kotlinx.serialization.Serializable

enum class BookFormat(val label: String) {
    EPUB("EPUB"),
    PDF("PDF"),
    MOBI("MOBI"),
    AZW("AZW"),
    TXT("Text"),
    KFX("KFX"),
    UNKNOWN("Book");

    val isReflowable: Boolean
        get() = this == EPUB || this == MOBI || this == AZW || this == TXT

    companion object {
        fun fromExtension(name: String): BookFormat {
            return when (name.substringAfterLast('.', "").lowercase()) {
                "epub" -> EPUB
                "pdf" -> PDF
                "mobi", "prc" -> MOBI
                "azw", "azw3" -> AZW
                "txt", "text" -> TXT
                "kfx" -> KFX
                else -> UNKNOWN
            }
        }
    }
}

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val format: BookFormat,
    // Absolute path of the imported book file inside app storage.
    val filePath: String,
    // Absolute path of an extracted cover image, or null.
    val coverPath: String? = null,
    val dateAddedMillis: Long = System.currentTimeMillis(),
    // 0f..1f reading progress.
    val progress: Float = 0f,
    // Format-specific resume location (chapter index, page number, scroll offset...).
    val locator: String = "",
    val lastOpenedMillis: Long = 0L
) {
    val isFinished: Boolean get() = progress >= 0.999f
    val isStarted: Boolean get() = progress > 0f
}

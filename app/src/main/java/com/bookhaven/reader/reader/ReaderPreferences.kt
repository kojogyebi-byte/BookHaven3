package com.bookhaven.reader.reader

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

enum class PageTheme(val label: String, val bg: Color, val text: Color, val cssBg: String, val cssText: String) {
    WHITE("White", Color(0xFFFFFFFF), Color(0xFF1C1C1E), "#ffffff", "#1c1c1e"),
    SEPIA("Sepia", Color(0xFFF6ECD9), Color(0xFF4A3F2E), "#f6ecd9", "#4a3f2e"),
    GRAY("Gray", Color(0xFF4E4E4E), Color(0xFFE8E8E8), "#4e4e4e", "#e8e8e8"),
    NIGHT("Night", Color(0xFF000000), Color(0xFFB8B8B8), "#000000", "#b8b8b8");

    val isDark: Boolean get() = this == GRAY || this == NIGHT
}

/**
 * Selectable reading typefaces. [cssStack] is used inside the EPUB WebView;
 * [composeFamily] is used for plain-text / MOBI reading.
 */
enum class ReaderFont(val label: String, val cssStack: String, val composeFamily: FontFamily) {
    ORIGINAL("Original", "inherit", FontFamily.Default),
    SERIF("Serif", "Georgia, 'Times New Roman', serif", FontFamily.Serif),
    SANS("Sans Serif", "'Helvetica Neue', Roboto, Arial, sans-serif", FontFamily.SansSerif),
    SLAB("Slab", "'Rockwell', 'Roboto Slab', Georgia, serif", FontFamily.Serif),
    MONO("Mono", "'Courier New', monospace", FontFamily.Monospace);
}

/** Persisted reading preferences shared across all books. */
class ReaderPreferences(context: Context) {
    private val sp = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

    var pageTheme: PageTheme
        get() = runCatching { PageTheme.valueOf(sp.getString("theme", "WHITE")!!) }.getOrDefault(PageTheme.WHITE)
        set(v) = sp.edit().putString("theme", v.name).apply()

    /** Font scale in percent, 70..260. */
    var fontScale: Int
        get() = sp.getInt("font", 110)
        set(v) = sp.edit().putInt("font", v.coerceIn(70, 260)).apply()

    var font: ReaderFont
        get() = runCatching { ReaderFont.valueOf(sp.getString("font_family", "SERIF")!!) }.getOrDefault(ReaderFont.SERIF)
        set(v) = sp.edit().putString("font_family", v.name).apply()

    var lineHeight: Float
        get() = sp.getFloat("line", 1.6f)
        set(v) = sp.edit().putFloat("line", v).apply()

    var paged: Boolean
        get() = sp.getBoolean("paged", true)
        set(v) = sp.edit().putBoolean("paged", v).apply()
}

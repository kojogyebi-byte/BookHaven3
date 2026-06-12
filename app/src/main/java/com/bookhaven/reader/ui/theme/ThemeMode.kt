package com.bookhaven.reader.ui.theme

import android.content.Context

/** Overall app appearance, independent of the in-reader page color. */
enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}

object AppPrefs {
    private const val FILE = "app_prefs"
    private const val KEY_THEME = "theme_mode"

    fun themeMode(ctx: Context): ThemeMode = runCatching {
        ThemeMode.valueOf(
            ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_THEME, ThemeMode.SYSTEM.name)!!
        )
    }.getOrDefault(ThemeMode.SYSTEM)

    fun setThemeMode(ctx: Context, mode: ThemeMode) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode.name).apply()
    }
}

package com.bookhaven.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookhaven.reader.data.BookFormat
import com.bookhaven.reader.format.MobiDrmException
import com.bookhaven.reader.format.MobiParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TextReaderScreen(
    filePath: String,
    format: BookFormat,
    initialLocator: String,
    prefs: ReaderPreferences,
    onBack: () -> Unit,
    onProgress: (Float, String) -> Unit
) {
    var theme by remember { mutableStateOf(prefs.pageTheme) }
    var fontScale by remember { mutableIntStateOf(prefs.fontScale) }
    var font by remember { mutableStateOf(prefs.font) }
    var chromeVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val content by produceState(initialValue = "", filePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                when (format) {
                    BookFormat.TXT -> File(filePath).readText()
                    BookFormat.MOBI, BookFormat.AZW -> MobiParser.readText(File(filePath))
                    else -> File(filePath).readText()
                }
            }.getOrElse { e ->
                if (e is MobiDrmException) "__DRM__" else "This file could not be read as text."
            }
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(content) {
        initialLocator.toIntOrNull()?.let { scroll.scrollTo(it) }
    }
    LaunchedEffect(scroll.value, scroll.maxValue) {
        val p = if (scroll.maxValue == 0) 0f else scroll.value.toFloat() / scroll.maxValue
        onProgress(p.coerceIn(0f, 1f), scroll.value.toString())
    }

    Box(Modifier.fillMaxSize().background(theme.bg)) {
        when {
            content.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            content == "__DRM__" -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Protected book", style = MaterialTheme.typography.titleLarge, color = theme.text)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This book is DRM-protected and can't be opened. Only DRM-free MOBI/AZW files are supported.",
                        style = MaterialTheme.typography.bodyMedium, color = theme.text,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onBack) { Text("Back to Library") }
                }
            }
            else -> SelectionContainer {
                Text(
                    text = content,
                    color = theme.text,
                    fontFamily = font.composeFamily,
                    fontSize = (17 * fontScale / 100).sp,
                    lineHeight = (17 * fontScale / 100 * prefs.lineHeight).sp,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { chromeVisible = !chromeVisible })
                        }
                )
            }
        }

        if (chromeVisible) {
            ReaderTopBar(
                title = File(filePath).nameWithoutExtension,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                Row(Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(if (scroll.maxValue == 0) 0 else scroll.value * 100 / scroll.maxValue)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSettings = true }) {
                        Text("Aa", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (showSettings) {
            AaSettingsSheet(
                prefs = prefs, currentTheme = theme, fontScale = fontScale, currentFont = font,
                onThemeChange = { theme = it; prefs.pageTheme = it },
                onFontChange = { fontScale = it; prefs.fontScale = it },
                onFontFamilyChange = { font = it; prefs.font = it },
                showLayoutToggle = false,
                onDismiss = { showSettings = false }
            )
        }
    }
}

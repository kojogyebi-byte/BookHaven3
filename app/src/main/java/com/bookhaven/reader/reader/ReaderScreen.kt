package com.bookhaven.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bookhaven.reader.data.Book
import com.bookhaven.reader.data.BookFormat

/**
 * Routes a [Book] to the correct reader implementation based on its format.
 * EPUB  -> [EpubReaderScreen] (WebView, paginated, Aa settings)
 * PDF   -> [PdfReaderScreen]  (PdfRenderer, pinch-zoom)
 * TXT / MOBI / AZW -> [TextReaderScreen] (reflowable text)
 * KFX / UNKNOWN    -> unsupported message
 */
@Composable
fun ReaderScreen(
    book: Book,
    onBack: () -> Unit,
    onProgress: (Float, String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { ReaderPreferences(context) }

    when (book.format) {
        BookFormat.EPUB -> EpubReaderScreen(
            filePath = book.filePath,
            initialLocator = book.locator,
            prefs = prefs,
            onBack = onBack,
            onProgress = onProgress
        )

        BookFormat.PDF -> PdfReaderScreen(
            filePath = book.filePath,
            initialLocator = book.locator,
            prefs = prefs,
            onBack = onBack,
            onProgress = onProgress
        )

        BookFormat.TXT, BookFormat.MOBI, BookFormat.AZW -> TextReaderScreen(
            filePath = book.filePath,
            format = book.format,
            initialLocator = book.locator,
            prefs = prefs,
            onBack = onBack,
            onProgress = onProgress
        )

        BookFormat.KFX -> UnsupportedFormat(
            message = "KFX books use Amazon's proprietary DRM and cannot be opened by third-party readers. " +
                "Please convert to EPUB or a DRM-free format first.",
            onBack = onBack
        )

        BookFormat.UNKNOWN -> UnsupportedFormat(
            message = "This file format isn't supported. BookHaven reads EPUB, PDF, TXT, and DRM-free MOBI/AZW.",
            onBack = onBack
        )
    }
}

@Composable
private fun UnsupportedFormat(message: String, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Unsupported format",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text("Back to Library") }
        }
    }
}

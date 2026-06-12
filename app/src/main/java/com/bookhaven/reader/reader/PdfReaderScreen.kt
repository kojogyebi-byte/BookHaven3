package com.bookhaven.reader.reader

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Builds the color filter that gives a PDF page the selected page theme.
 * Because a PDF's text is baked into the page image, we can't change its font,
 * but we *can* recolor the whole page so it matches the EPUB reading themes:
 *  - White : untouched
 *  - Sepia : warm "paper" multiply
 *  - Gray  : softened invert (dark-gray background, light-gray text)
 *  - Night : full invert (black background, light text)
 */
private fun pdfColorFilter(theme: PageTheme): ColorFilter? = when (theme) {
    PageTheme.WHITE -> null
    PageTheme.SEPIA -> ColorFilter.tint(Color(0xFFF6ECD9), BlendMode.Multiply)
    PageTheme.GRAY -> ColorFilter.colorMatrix(invertMatrix(scale = -0.55f, offset = 200f))
    PageTheme.NIGHT -> ColorFilter.colorMatrix(invertMatrix(scale = -0.90f, offset = 230f))
}

private fun invertMatrix(scale: Float, offset: Float) = ColorMatrix(
    floatArrayOf(
        scale, 0f, 0f, 0f, offset,
        0f, scale, 0f, 0f, offset,
        0f, 0f, scale, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )
)

@Composable
fun PdfReaderScreen(
    filePath: String,
    initialLocator: String,
    prefs: ReaderPreferences,
    onBack: () -> Unit,
    onProgress: (Float, String) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val renderer = remember(filePath) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd) to pfd
        }.getOrNull()
    }
    DisposableEffect(renderer) {
        onDispose { renderer?.let { runCatching { it.first.close(); it.second.close() } } }
    }

    if (renderer == null || renderer.first.pageCount == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This PDF could not be opened.", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onBack) { Text("Back to Library") }
            }
        }
        return
    }

    val pdf = renderer.first
    val pageCount = pdf.pageCount
    val renderLock = remember { Any() }
    val startPage = remember { initialLocator.toIntOrNull()?.coerceIn(0, pageCount - 1) ?: 0 }
    val pagerState = rememberPagerState(initialPage = startPage) { pageCount }

    var theme by remember { mutableStateOf(prefs.pageTheme) }
    var chromeVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val colorFilter = remember(theme) { pdfColorFilter(theme) }

    val targetWidthPx = remember { with(density) { 1000.dp.toPx().toInt() }.coerceAtMost(2000) }

    LaunchedEffect(pagerState.currentPage) {
        onProgress((pagerState.currentPage + 1f) / pageCount, pagerState.currentPage.toString())
    }

    fun goToPage(target: Int) {
        val p = target.coerceIn(0, pageCount - 1)
        if (p != pagerState.currentPage) scope.launch { pagerState.animateScrollToPage(p) }
    }

    Box(Modifier.fillMaxSize().background(theme.bg)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val bitmap by produceState<Bitmap?>(initialValue = null, page, targetWidthPx) {
                value = withContext(Dispatchers.IO) {
                    synchronized(renderLock) {
                        runCatching {
                            pdf.openPage(page).use { p ->
                                val scale = targetWidthPx.toFloat() / p.width
                                val w = targetWidthPx
                                val h = (p.height * scale).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(AColor.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }.getOrNull()
                    }
                }
            }

            var scale by remember(page) { mutableFloatStateOf(1f) }
            var offsetX by remember(page) { mutableFloatStateOf(0f) }
            var offsetY by remember(page) { mutableFloatStateOf(0f) }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                            else { offsetX = 0f; offsetY = 0f }
                        }
                    }
                    .pointerInput(page, pageCount) {
                        // EPUB-style tap zones: left = back, right = forward, centre = toggle chrome
                        detectTapGestures { o ->
                            val w = size.width
                            when {
                                o.x < w * 0.30f -> goToPage(page - 1)
                                o.x > w * 0.70f -> goToPage(page + 1)
                                else -> chromeVisible = !chromeVisible
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${page + 1}",
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale, scaleY = scale,
                                translationX = offsetX, translationY = offsetY
                            )
                    )
                } else {
                    CircularProgressIndicator(color = theme.text)
                }
            }
        }

        if (chromeVisible) {
            ReaderTopBar(
                title = File(filePath).nameWithoutExtension,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            PdfBottomBar(
                page = pagerState.currentPage,
                pageCount = pageCount,
                onPrev = { goToPage(pagerState.currentPage - 1) },
                onNext = { goToPage(pagerState.currentPage + 1) },
                onAa = { showSettings = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showSettings) {
            AaSettingsSheet(
                prefs = prefs,
                currentTheme = theme,
                fontScale = prefs.fontScale,
                currentFont = prefs.font,
                onThemeChange = { theme = it; prefs.pageTheme = it },
                onFontChange = {},
                onFontFamilyChange = {},
                showFontControls = false,
                showLayoutToggle = false,
                note = "PDF pages keep their original layout and fonts, so type size and " +
                    "typeface can't be changed. The page color themes above still apply.",
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
private fun PdfBottomBar(
    page: Int, pageCount: Int,
    onPrev: () -> Unit, onNext: () -> Unit, onAa: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrev) { Text("Prev") }
                Spacer(Modifier.weight(1f))
                Text(
                    "Page ${page + 1} of $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onNext) { Text("Next") }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onAa) { Text("Aa", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

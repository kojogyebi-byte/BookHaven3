package com.bookhaven.reader.reader

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bookhaven.reader.format.EpubBook
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EpubReaderScreen(
    filePath: String,
    initialLocator: String,
    prefs: ReaderPreferences,
    onBack: () -> Unit,
    onProgress: (Float, String) -> Unit
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val epub = remember(filePath) { runCatching { EpubBook.open(File(filePath)) }.getOrNull() }
    DisposableEffect(epub) { onDispose { epub?.close() } }

    if (epub == null || epub.chapterCount == 0) {
        ReaderError(onBack); return
    }

    var theme by remember { mutableStateOf(prefs.pageTheme) }
    var fontScale by remember { mutableIntStateOf(prefs.fontScale) }
    var font by remember { mutableStateOf(prefs.font) }
    var paged by remember { mutableStateOf(prefs.paged) }
    var chromeVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val (startChapter, startPage) = remember {
        val parts = initialLocator.split(":")
        (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
    var chapterIndex by remember { mutableIntStateOf(startChapter.coerceIn(0, epub.chapterCount - 1)) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(1) }
    // After (re)pagination, jump to this page. -1 means "last page".
    var pendingPage by remember { mutableStateOf<Int?>(startPage) }

    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun reportProgress() {
        val overall = if (epub.chapterCount <= 1 && pageCount <= 1) 0f
        else (chapterIndex + (currentPage.toFloat() / pageCount.coerceAtLeast(1))) / epub.chapterCount
        onProgress(overall.coerceIn(0f, 1f), "$chapterIndex:$currentPage")
    }

    fun goTo(page: Int) {
        currentPage = page.coerceIn(0, pageCount - 1)
        webViewRef.value?.evaluateJavascript("window.__goTo(${currentPage});", null)
        reportProgress()
    }

    fun loadChapter(index: Int, restore: Int?) {
        chapterIndex = index.coerceIn(0, epub.chapterCount - 1)
        pendingPage = restore
        val wv = webViewRef.value ?: return
        val html = buildEpubHtml(epub.chapterHtml(chapterIndex), theme, fontScale, prefs.lineHeight, paged, font)
        wv.loadDataWithBaseURL(epub.chapterBaseUrl(chapterIndex), html, "text/html", "UTF-8", null)
    }

    fun nextPage() {
        if (currentPage < pageCount - 1) goTo(currentPage + 1)
        else if (chapterIndex < epub.chapterCount - 1) loadChapter(chapterIndex + 1, 0)
    }
    fun prevPage() {
        if (currentPage > 0) goTo(currentPage - 1)
        else if (chapterIndex > 0) loadChapter(chapterIndex - 1, -1)
    }

    val bridge = remember {
        object {
            @JavascriptInterface
            fun onPaginated(count: Int) {
                mainHandler.post {
                    pageCount = count.coerceAtLeast(1)
                    val target = when (val p = pendingPage) {
                        null -> currentPage
                        -1 -> pageCount - 1
                        else -> p
                    }
                    pendingPage = null
                    goTo(target)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(theme.bg)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                @SuppressLint("SetJavaScriptEnabled")
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addJavascriptInterface(bridge, "Android")
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            epub.openResource(url)?.let { (stream, mime) ->
                                return WebResourceResponse(mime, "UTF-8", stream)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    webViewRef.value = this
                    val html = buildEpubHtml(epub.chapterHtml(chapterIndex), theme, fontScale, prefs.lineHeight, paged, font)
                    loadDataWithBaseURL(epub.chapterBaseUrl(chapterIndex), html, "text/html", "UTF-8", null)
                }
            }
        )

        // Gesture overlay: tap zones + horizontal swipe
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(paged, pageCount) {
                    detectTapGestures { offset ->
                        val w = size.width
                        when {
                            offset.x < w * 0.30f -> prevPage()
                            offset.x > w * 0.70f -> nextPage()
                            else -> chromeVisible = !chromeVisible
                        }
                    }
                }
                .pointerInput(paged, pageCount) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onHorizontalDrag = { _, d -> dx += d },
                        onDragEnd = {
                            if (abs(dx) > 60) { if (dx < 0) nextPage() else prevPage() }
                        }
                    )
                }
        )

        if (chromeVisible) {
            ReaderTopBar(
                title = epub.tocTitles.getOrElse(chapterIndex) { "Chapter ${chapterIndex + 1}" },
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            EpubBottomBar(
                chapterIndex = chapterIndex,
                chapterCount = epub.chapterCount,
                page = currentPage,
                pageCount = pageCount,
                onPrev = { prevPage() },
                onNext = { nextPage() },
                onAa = { showSettings = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showSettings) {
            AaSettingsSheet(
                prefs = prefs,
                currentTheme = theme,
                fontScale = fontScale,
                currentFont = font,
                onThemeChange = { theme = it; prefs.pageTheme = it; loadChapter(chapterIndex, currentPage) },
                onFontChange = { fontScale = it; prefs.fontScale = it; loadChapter(chapterIndex, currentPage) },
                onFontFamilyChange = { font = it; prefs.font = it; loadChapter(chapterIndex, currentPage) },
                paged = paged,
                onPagedChange = { paged = it; prefs.paged = it; loadChapter(chapterIndex, 0) },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
private fun EpubBottomBar(
    chapterIndex: Int, chapterCount: Int, page: Int, pageCount: Int,
    onPrev: () -> Unit, onNext: () -> Unit, onAa: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrev) { Text("Prev") }
                Spacer(Modifier.weight(1f))
                Text(
                    "Chapter ${chapterIndex + 1} of $chapterCount  ·  ${page + 1}/$pageCount",
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

@Composable
private fun ReaderError(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This book could not be opened.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("Back to Library") }
        }
    }
}

/** Wraps chapter XHTML with theme CSS + pagination JS. */
private fun buildEpubHtml(
    raw: String, theme: PageTheme, fontScale: Int, lineHeight: Float, paged: Boolean, font: ReaderFont
): String {
    val fontRule = if (font == ReaderFont.ORIGINAL) ""
        else "font-family: ${font.cssStack} !important;"
    val css = """
        <style id="bh-style">
        html { -webkit-text-size-adjust: none; }
        html, body { background: ${theme.cssBg} !important; }
        body {
            color: ${theme.cssText} !important;
            font-size: ${fontScale}% !important;
            line-height: ${lineHeight} !important;
            $fontRule
            text-align: justify;
            -webkit-hyphens: auto; hyphens: auto;
            word-wrap: break-word;
        }
        p, div, span, li, a, h1, h2, h3, h4, h5, h6 { ${if (font == ReaderFont.ORIGINAL) "" else "font-family: ${font.cssStack} !important;"} }
        img, svg, image { max-width: 100% !important; height: auto !important; }
        a { color: inherit !important; text-decoration: none; }
        p { margin: 0 0 0.8em 0; }
        </style>
    """.trimIndent()

    val js = if (paged) """
        <script>
        (function(){
          var HPAD=24, VPAD=30;
          window.__step=window.innerWidth;
          function layout(){
            var b=document.body;
            document.documentElement.style.height='100%';
            document.documentElement.style.overflow='hidden';
            b.style.margin='0';
            b.style.height=(window.innerHeight-2*VPAD)+'px';
            b.style.padding=VPAD+'px '+HPAD+'px';
            b.style.columnWidth=(window.innerWidth-2*HPAD)+'px';
            b.style.columnGap=(2*HPAD)+'px';
            b.style.columnFill='auto';
            b.style.overflow='hidden';
            b.style.transform='translateX(0)';
            window.__step=window.innerWidth;
            var total=Math.max(1, Math.round(b.scrollWidth/window.__step));
            if(window.Android&&window.Android.onPaginated){window.Android.onPaginated(total);}
          }
          window.__goTo=function(p){ document.body.style.transform='translateX('+(-p*window.__step)+'px)'; };
          var done=false; var run=function(){ if(done)return; done=true; layout(); };
          if(document.readyState==='complete'){ setTimeout(run,40); }
          else { window.addEventListener('load', function(){ setTimeout(run,40); }); }
          setTimeout(function(){ if(!done){ run(); } }, 350);
        })();
        </script>
    """.trimIndent() else """
        <script>
        window.__goTo=function(p){};
        (function(){ if(window.Android&&window.Android.onPaginated){window.Android.onPaginated(1);} })();
        </script>
    """.trimIndent()

    val injected = css + js
    return when {
        raw.contains("</head>", true) -> raw.replaceFirst(Regex("(?i)</head>"), "$injected</head>")
        raw.contains("<body", true) -> raw.replaceFirst(Regex("(?i)(<body[^>]*>)"), "$1$injected")
        else -> "<html><head>$injected</head><body>$raw</body></html>"
    }
}

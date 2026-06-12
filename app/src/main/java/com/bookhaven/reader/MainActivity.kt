package com.bookhaven.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bookhaven.reader.reader.ReaderScreen
import com.bookhaven.reader.ui.LibraryScreen
import com.bookhaven.reader.ui.theme.BookHavenTheme
import com.bookhaven.reader.viewmodel.LibraryViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    // Holds a book Uri delivered via an external VIEW intent ("Open with BookHaven").
    private val pendingViewUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val ctx = this
            var themeMode by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(
                    com.bookhaven.reader.ui.theme.AppPrefs.themeMode(ctx)
                )
            }
            val dark = when (themeMode) {
                com.bookhaven.reader.ui.theme.ThemeMode.LIGHT -> false
                com.bookhaven.reader.ui.theme.ThemeMode.DARK -> true
                com.bookhaven.reader.ui.theme.ThemeMode.SYSTEM ->
                    androidx.compose.foundation.isSystemInDarkTheme()
            }
            BookHavenTheme(darkTheme = dark) {
                BookHavenApp(
                    pendingViewUri = pendingViewUri,
                    isDark = dark,
                    onToggleTheme = {
                        val next = if (dark) com.bookhaven.reader.ui.theme.ThemeMode.LIGHT
                        else com.bookhaven.reader.ui.theme.ThemeMode.DARK
                        themeMode = next
                        com.bookhaven.reader.ui.theme.AppPrefs.setThemeMode(ctx, next)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                // Persist read access where the grant allows it.
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                pendingViewUri.value = uri
            }
        }
    }
}

private object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}"
    fun reader(bookId: String) = "reader/$bookId"
}

@Composable
private fun BookHavenApp(
    pendingViewUri: MutableStateFlow<Uri?>,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val nav = rememberNavController()
    val vm: LibraryViewModel = viewModel()
    val books by vm.books.collectAsState()
    val importing by vm.importing.collectAsState()

    // Multi-file picker via the Storage Access Framework.
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importBooks(uris) }

    // When an external app opens a book with BookHaven, import it then jump
    // straight into the most recently added book once the library updates.
    var awaitingIntentOpen by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val incoming by pendingViewUri.collectAsState()
    LaunchedEffect(incoming) {
        val uri = incoming ?: return@LaunchedEffect
        pendingViewUri.value = null
        awaitingIntentOpen = true
        vm.importBooks(listOf(uri))
    }
    LaunchedEffect(books, importing) {
        if (awaitingIntentOpen && !importing && books.isNotEmpty()) {
            awaitingIntentOpen = false
            val newest = books.maxByOrNull { it.dateAddedMillis } ?: return@LaunchedEffect
            nav.navigate(Routes.reader(newest.id))
        }
    }

    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                books = books,
                importing = importing,
                isDark = isDark,
                onToggleTheme = onToggleTheme,
                onOpenBook = { book -> nav.navigate(Routes.reader(book.id)) },
                onImport = {
                    picker.launch(
                        arrayOf(
                            "application/epub+zip",
                            "application/pdf",
                            "application/x-mobipocket-ebook",
                            "application/vnd.amazon.ebook",
                            "text/plain",
                            "application/octet-stream"
                        )
                    )
                },
                onDelete = { vm.delete(it) }
            )
        }
        composable(Routes.READER) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")
            val book = bookId?.let { vm.book(it) }
            if (book == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                ReaderScreen(
                    book = book,
                    onBack = { nav.popBackStack() },
                    onProgress = { progress, locator ->
                        vm.updateProgress(book.id, progress, locator)
                    }
                )
            }
        }
    }
}

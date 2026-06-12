package com.bookhaven.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookhaven.reader.data.Book
import com.bookhaven.reader.ui.components.BookCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    books: List<Book>,
    importing: Boolean,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onImport: () -> Unit,
    onDelete: (Book) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }

    val filtered = remember(books, query) {
        if (query.isBlank()) books
        else books.filter {
            it.title.contains(query, true) || it.author.contains(query, true)
        }
    }
    val readingNow = remember(books) {
        books.filter { it.isStarted && !it.isFinished }
            .sortedByDescending { it.lastOpenedMillis }
            .take(8)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Book") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 118.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.statusBarsPadding())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Library",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 8.dp, bottom = 12.dp)
                        )
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importing) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Importing…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (readingNow.isNotEmpty() && query.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Reading Now")
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ReadingNowRow(readingNow, onOpenBook)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("All Books")
                }
            }

            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(hasQuery = query.isNotBlank())
                }
            }

            items(filtered, key = { it.id }) { book ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenBook(book) }
                        .padding(2.dp)
                ) {
                    Box {
                        BookCover(book, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        book.author,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (book.isStarted) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { book.progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    TextButton(
                        onClick = { pendingDelete = book },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Created by Kwadwo Gyebi · Shamaapps",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp)
                )
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove book?") },
            text = { Text("“${book.title}” will be removed from your library.") },
            confirmButton = {
                TextButton(onClick = { onDelete(book); pendingDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ReadingNowRow(books: List<Book>, onOpen: (Book) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.foundation.lazy.items(books, key = { it.id }) { b ->
            Column(Modifier.width(120.dp).clickable { onOpen(b) }) {
                BookCover(b, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { b.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp).clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun EmptyState(hasQuery: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (hasQuery) "No matches" else "Your library is empty",
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasQuery) "Try a different search." else "Tap “Add Book” to import EPUB, PDF, MOBI or TXT files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

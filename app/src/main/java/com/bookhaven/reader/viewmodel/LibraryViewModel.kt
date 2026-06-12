package com.bookhaven.reader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bookhaven.reader.data.Book
import com.bookhaven.reader.data.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(app)

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    init {
        viewModelScope.launch {
            _books.value = withContext(Dispatchers.IO) { repo.load() }
        }
    }

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            val updated = withContext(Dispatchers.IO) {
                var current = _books.value
                uris.forEach { current = repo.importFromUri(it, current) }
                current
            }
            _books.value = updated
            _importing.value = false
        }
    }

    fun book(id: String): Book? = _books.value.firstOrNull { it.id == id }

    fun updateProgress(id: String, progress: Float, locator: String) {
        val b = book(id) ?: return
        val updated = b.copy(progress = progress, locator = locator, lastOpenedMillis = System.currentTimeMillis())
        viewModelScope.launch {
            _books.value = withContext(Dispatchers.IO) { repo.updateBook(_books.value, updated) }
        }
    }

    fun delete(book: Book) {
        viewModelScope.launch {
            _books.value = withContext(Dispatchers.IO) { repo.deleteBook(_books.value, book) }
        }
    }
}

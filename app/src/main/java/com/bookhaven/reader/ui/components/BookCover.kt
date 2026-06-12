package com.bookhaven.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bookhaven.reader.data.Book
import java.io.File
import kotlin.math.absoluteValue

private val palette = listOf(
    listOf(Color(0xFF6D597A), Color(0xFF355070)),
    listOf(Color(0xFFB56576), Color(0xFFE56B6F)),
    listOf(Color(0xFF1A759F), Color(0xFF34A0A4)),
    listOf(Color(0xFF583101), Color(0xFF8A5A44)),
    listOf(Color(0xFF22223B), Color(0xFF4A4E69)),
    listOf(Color(0xFF2B9348), Color(0xFF007F5F))
)

@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.66f)
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val cover = book.coverPath?.let { File(it) }
        if (cover != null && cover.exists()) {
            AsyncImage(
                model = cover,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val colors = palette[(book.title.hashCode().absoluteValue) % palette.size]
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors))
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Format chip
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(book.format.label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

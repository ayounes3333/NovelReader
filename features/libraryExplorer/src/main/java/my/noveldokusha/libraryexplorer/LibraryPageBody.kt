package my.noveldokusha.libraryexplorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.components.BookImageButtonView
import my.noveldoksuha.coreui.modifiers.bounceOnPressed
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.ImageBorderShape
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.feature.local_database.BookWithContext

@Composable
internal fun LibraryPageBody(
    list: List<BookGroup>,
    onClick: (BookWithContext) -> Unit,
    onLongClick: (BookWithContext) -> Unit,
) {
    var expandedGroup by remember { mutableStateOf<BookGroup?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 400.dp, start = 4.dp, end = 4.dp)
    ) {
        items(
            items = list,
            key = { group ->
                if (group.books.size == 1) group.books[0].book.url
                else "grp:${group.seriesTitle}"
            }
        ) { group ->
            val interactionSource = remember { MutableInteractionSource() }
            val book = group.representative
            Box {
                BookImageButtonView(
                    title = group.seriesTitle,
                    coverImageModel = rememberResolvedBookImagePath(
                        bookUrl = book.book.url,
                        imagePath = book.book.coverImageUrl
                    ),
                    onClick = {
                        if (group.books.size == 1) onClick(group.books[0])
                        else expandedGroup = group
                    },
                    onLongClick = { onLongClick(book) },
                    interactionSource = interactionSource,
                    modifier = Modifier.bounceOnPressed(interactionSource)
                )
                // Unread chapters badge (summed across all books in group)
                val notReadCount = group.books.sumOf { it.chaptersCount - it.chaptersReadCount }
                AnimatedVisibility(
                    visible = notReadCount != 0,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = notReadCount.toString(),
                        color = Color.White,
                        modifier = Modifier
                            .padding(8.dp)
                            .background(ColorAccent, ImageBorderShape)
                            .padding(4.dp)
                    )
                }
                // Group count badge (only shown when group contains multiple books)
                if (group.books.size > 1) {
                    Text(
                        text = group.books.size.toString(),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.secondary, ImageBorderShape)
                            .padding(4.dp)
                    )
                }
                if (book.book.url.isLocalUri) Text(
                    text = stringResource(R.string.local),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(ColorAccent, ImageBorderShape)
                        .padding(4.dp)
                )
            }
        }
    }

    expandedGroup?.let { group ->
        BookGroupSelectionDialog(
            group = group,
            onBookClick = { book ->
                expandedGroup = null
                onClick(book)
            },
            onDismiss = { expandedGroup = null }
        )
    }
}

@Composable
private fun BookGroupSelectionDialog(
    group: BookGroup,
    onBookClick: (BookWithContext) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(group.seriesTitle) },
        text = {
            LazyColumn {
                items(group.books, key = { it.book.url }) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookClick(book) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = book.book.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

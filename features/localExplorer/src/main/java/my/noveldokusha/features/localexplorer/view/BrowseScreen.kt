package my.noveldokusha.features.localexplorer.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldoksuha.coreui.components.ErrorView
import my.noveldoksuha.coreui.components.Loading
import my.noveldoksuha.coreui.components.LoadingSnackbar
import my.noveldoksuha.coreui.components.NoData
import my.noveldoksuha.coreui.components.OnLifecycleEvent
import my.noveldoksuha.coreui.components.Viewing
import my.noveldoksuha.coreui.theme.ColorAccent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow

import my.noveldokusha.features.localexplorer.BrowseScreenState
import my.noveldokusha.features.localexplorer.FileManager
import my.noveldokusha.feature.local_database.tables.localexplorer.NovelFileInfo
import my.noveldokusha.features.localexplorer.model.Browsable
import my.noveldokusha.features.localexplorer.R
import my.noveldokusha.features.localexplorer.extractor.data.dateFormatted
import my.noveldokusha.features.localexplorer.extractor.data.fileSize
import my.noveldokusha.features.localexplorer.extractor.utils.content
import my.noveldokusha.features.localexplorer.viewmodel.BrowseData
import my.noveldokusha.features.localexplorer.viewmodel.BrowseViewModel
import java.io.File

// Strips the legacy " - " prefix produced by older metadata extraction.
fun String.cleanBookTitle(): String = trimStart().removePrefix("-").trimStart()

// Utility function to truncate text in the middle, showing start and end
fun String.truncateMiddle(maxLength: Int = 40): String {
    if (this.length <= maxLength) return this

    val startChars = maxLength / 2 - 2
    val endChars = maxLength / 2 - 2

    return "${this.take(startChars)}...${this.takeLast(endChars)}"
}

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onBookClick: (file: File) -> Unit
) {
    var viewing by remember {
        mutableStateOf(FileManager.viewing)
    }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        Dialog(
            onDismissRequest = { showDialog = false },
            DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                CircularProgressIndicator(color = ColorAccent)
            }
        }
    }

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                showDialog = false
            }

            Lifecycle.Event.ON_CREATE -> {}
            Lifecycle.Event.ON_START -> {}
            Lifecycle.Event.ON_PAUSE -> {
                showDialog = false
            }

            Lifecycle.Event.ON_STOP -> {}
            Lifecycle.Event.ON_DESTROY -> {}
            Lifecycle.Event.ON_ANY -> {}
            else -> {}
        }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            BrowseSearchField(
                modifier = Modifier.weight(1f),
                query = viewModel.query.value,
                onQueryChange = {
                    viewModel.setSearchText(it)
                    if (it.isEmpty()) viewModel.clearSearch() else viewModel.search()
                },
                onClear = { viewModel.clearSearch() },
                onFocusChange = viewModel::setSearchFocused,
            )
            Viewing(tint = MaterialTheme.colorScheme.onPrimary, viewing = viewing) {
                viewing = it
                FileManager.viewing = it
            }
        }
        viewModel.uiState.let { uiState ->
            when (uiState) {
                is BrowseScreenState.NoData -> {
                    NoData(message = "No Files found!")
                }

                is BrowseScreenState.Data -> {
                    Files(
                        viewing = viewing,
                        browseData = uiState.data,
                        onBookClick = { file ->
                            showDialog = true
                            onBookClick(file)
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onDelete = viewModel::deleteFile,
                        folderClicked = { viewModel.browse(it) }
                    )
                }

                is BrowseScreenState.Error -> {
                    uiState.error.printStackTrace()
                    ErrorView(
                        error = "Unable to browse directory",
                        onReload = {
                            viewModel.browse(FileManager.getCurrentDirectory())
                        }
                    )
                }

                is BrowseScreenState.Loading -> {
                    if (uiState.data?.browsable.isNullOrEmpty())
                        Loading()
                    else
                        Files(
                            viewing = viewing,
                            browseData = uiState.data!!,
                            isLoading = true,
                            onBookClick = { file ->
                                showDialog = true
                                onBookClick(file)
                            },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onDelete = viewModel::deleteFile,
                            folderClicked = { viewModel.browse(it) }
                        )
                }
            }
        }
    }
}

@Composable
fun FolderListItem(
    modifier: Modifier = Modifier,
    directory: File,
    onFolderClick: (directory: File) -> Unit = {}
) {
    var isEmpty by remember(directory) { mutableStateOf(false) }
    var content by remember(directory) { mutableStateOf("") }

    LaunchedEffect(directory) {
        withContext(Dispatchers.IO) {
            isEmpty = directory.listFiles()?.isEmpty() ?: true
            content = directory.content
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .clickable {
                if (directory.isDirectory)
                    onFolderClick(directory)
            }
    ) {
        Icon(
            modifier = Modifier
                .size(40.dp)
                .padding(4.dp),
            imageVector = ImageVector
                .vectorResource(
                    id = if (isEmpty)
                        R.drawable.folder_empty
                    else
                        R.drawable.folder
                ),
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = "File Icon"
        )
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = directory.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (content.isNotEmpty()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun FolderGridItem(
    modifier: Modifier = Modifier,
    directory: File,
    onFolderClick: (directory: File) -> Unit = {}
) {
    var isEmpty by remember(directory) { mutableStateOf(false) }
    var content by remember(directory) { mutableStateOf("") }

    LaunchedEffect(directory) {
        withContext(Dispatchers.IO) {
            isEmpty = directory.listFiles()?.isEmpty() ?: true
            content = directory.content
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .clickable {
                if (directory.isDirectory)
                    onFolderClick(directory)
            }
    ) {
        Icon(
            modifier = Modifier
                .size(56.dp)
                .padding(8.dp),
            imageVector = ImageVector
                .vectorResource(
                    id = if (isEmpty)
                        R.drawable.folder_empty
                    else
                        R.drawable.folder
                ),
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = "File Icon"
        )
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = directory.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (content.isNotEmpty()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun Files(
    viewing: Viewing,
    browseData: BrowseData,
    isLoading: Boolean = false,
    onBookClick: (file: File) -> Unit,
    onToggleFavorite: (NovelFileInfo) -> Unit = {},
    onDelete: (NovelFileInfo) -> Unit = {},
    folderClicked: (file: File) -> Unit
) {
    val snackbarHostState = remember {
        SnackbarHostState()
    }
    var pendingDelete by remember { mutableStateOf<NovelFileInfo?>(null) }
    pendingDelete?.let { target ->
        DeleteFileDialog(
            title = target.title.cleanBookTitle(),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDelete(target)
                pendingDelete = null
            }
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(key1 = isLoading) {
            if (isLoading) {
                launch {
                    snackbarHostState.showSnackbar("Loading...")
                }
            }
        }
        Column {
            FilesHeader(parents = browseData.parents, parentClicked = folderClicked)
            if (browseData.browsable.isEmpty()) {
                NoData(message = "No Files found!")
            } else {
                when (viewing) {
                    Viewing.LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(
                                count = browseData.browsable.size,
                                key = { index -> browseData.browsable[index].file.absolutePath },
                                contentType = { index ->
                                    if (browseData.browsable[index].isDirectory) "folder" else "book"
                                }
                            ) { index ->
                                val item = browseData.browsable[index]
                                if (item.isDirectory) {
                                    FolderListItem(
                                        directory = item.file,
                                        onFolderClick = folderClicked
                                    )
                                } else {
                                    NovelFileListItem(
                                        novelFileInfo = item.novelFileInfo!!,
                                        onBookClick = onBookClick,
                                        onToggleFavorite = onToggleFavorite,
                                        onDelete = { pendingDelete = it }
                                    )
                                }
                            }
                        }
                    }

                    Viewing.GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(5.dp),
                        ) {
                            items(
                                count = browseData.browsable.size,
                                key = { index -> browseData.browsable[index].file.absolutePath },
                                contentType = { index ->
                                    if (browseData.browsable[index].isDirectory) "folder" else "book"
                                }
                            ) { index ->
                                val item = browseData.browsable[index]
                                if (item.isDirectory) {
                                    FolderGridItem(
                                        directory = item.file,
                                        onFolderClick = folderClicked
                                    )
                                } else {
                                    NovelFileGridItem(
                                        novelFileInfo = item.novelFileInfo!!,
                                        onBookClick = onBookClick,
                                        onToggleFavorite = onToggleFavorite,
                                        onDelete = { pendingDelete = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        LoadingSnackbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .wrapContentHeight(),
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
fun FilesHeader(
    parents: List<File>,
    parentClicked: (File) -> Unit
) {
    if (parents.isNotEmpty()) {
        BackHandler {
            parentClicked(parents.last())
        }
    }
    val currentDir = FileManager.getCurrentDirectory()
    val crumbs = remember(parents, currentDir) {
        val list = parents.drop(1) // first parent is storage root (shown as the home icon)
        if (currentDir != null && parents.isNotEmpty()) list + currentDir else list
    }
    val listState = rememberLazyListState()
    LaunchedEffect(crumbs.size) {
        if (crumbs.isNotEmpty()) listState.animateScrollToItem(crumbs.lastIndex)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            BreadcrumbChip(
                text = "Storage",
                icon = ImageVector.vectorResource(R.drawable.ic_home_black_24dp),
                selected = crumbs.isEmpty(),
                onClick = { FileManager.getStorageRoot()?.let(parentClicked) }
            )
        }
        items(crumbs.size) { index ->
            val crumb = crumbs[index]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.baseline_chevron_right_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                BreadcrumbChip(
                    text = crumb.name,
                    selected = index == crumbs.lastIndex,
                    onClick = { if (index != crumbs.lastIndex) parentClicked(crumb) }
                )
            }
        }
    }
}

@Composable
private fun BreadcrumbChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) ColorAccent.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp),
                tint = if (selected) ColorAccent else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) ColorAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
    }
}

@Composable
private fun BrowseSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(ColorAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChange(it.isFocused) }
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear",
                modifier = Modifier
                    .size(18.dp)
                    .clickable {
                        onClear()
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeleteFileDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete file?") },
        text = { Text("\"$title\" will be permanently removed from the device.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun NovelFileListItem(
    novelFileInfo: NovelFileInfo,
    onBookClick: (file: File) -> Unit,
    onToggleFavorite: (NovelFileInfo) -> Unit = {},
    onDelete: (NovelFileInfo) -> Unit = {},
) {
    // Load cover asynchronously to prevent UI freeze
    var novelCover by remember(novelFileInfo.path) {
        mutableStateOf<my.noveldokusha.feature.local_database.tables.localexplorer.Cover?>(
            null
        )
    }

    LaunchedEffect(novelFileInfo.path) {
        withContext(Dispatchers.Default) {
            novelCover = novelFileInfo.cover.invoke()
        }
    }

    // Cache expensive computations
    val fileInfoText = remember(novelFileInfo.path) {
        "${novelFileInfo.dateFormatted} ${novelFileInfo.fileSize}"
    }
    val progressText = remember(novelFileInfo.progress) {
        if (novelFileInfo.progress > 0) "${novelFileInfo.progress}%" else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onBookClick(File(novelFileInfo.path)) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover image with placeholder
        novelCover?.bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = novelCover!!.text,
                modifier = Modifier
                    .size(width = 72.dp, height = 100.dp)
                    .clip(MaterialTheme.shapes.small)
            )
        } ?: novelCover?.vector?.let { id ->
            Image(
                imageVector = ImageVector.vectorResource(id),
                contentDescription = novelCover!!.text,
                modifier = Modifier
                    .size(width = 72.dp, height = 100.dp)
                    .clip(MaterialTheme.shapes.small)
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 100.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = ColorAccent
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = novelFileInfo.title.cleanBookTitle().truncateMiddle(75),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = novelFileInfo.author,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileInfoText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (progressText.isNotEmpty()) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorAccent,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))

                // Actions
                Row {
                    Icon(
                        imageVector = ImageVector.vectorResource(
                            id = if (novelFileInfo.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
                        ),
                        contentDescription = "Favorite",
                        tint = if (novelFileInfo.isFavorite) ColorAccent else MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.6f
                        ),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onToggleFavorite(novelFileInfo) }
                            .padding(vertical = 4.dp)
                    )

                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_outline_delete_24),
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onDelete(novelFileInfo) }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NovelFileGridItem(
    novelFileInfo: NovelFileInfo,
    onBookClick: (file: File) -> Unit,
    onToggleFavorite: (NovelFileInfo) -> Unit = {},
    onDelete: (NovelFileInfo) -> Unit = {},
) {
    // Load cover asynchronously to prevent UI freeze
    var novelCover by remember(novelFileInfo.path) {
        mutableStateOf<my.noveldokusha.feature.local_database.tables.localexplorer.Cover?>(
            null
        )
    }

    LaunchedEffect(novelFileInfo.path) {
        withContext(Dispatchers.Default) {
            novelCover = novelFileInfo.cover.invoke()
        }
    }

    // Cache expensive computations
    val fileInfoText = remember(novelFileInfo.path) {
        "${novelFileInfo.dateFormatted} ${novelFileInfo.fileSize}"
    }
    val progressText = remember(novelFileInfo.progress) {
        if (novelFileInfo.progress > 0) "${novelFileInfo.progress}%" else ""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f) // Book cover aspect ratio (width:height = 7:10)
            .padding(4.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable { onBookClick(File(novelFileInfo.path)) }
    ) {
        // Cover image with placeholder (fills entire box)
        novelCover?.bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = novelCover!!.text,
                modifier = Modifier.fillMaxSize()
            )
        } ?: novelCover?.vector?.let { id ->
            Image(
                imageVector = ImageVector.vectorResource(id),
                contentDescription = novelCover!!.text,
                modifier = Modifier.fillMaxSize()
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = ColorAccent
                )
            }
        }

        // Gradient overlay at bottom for text readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.Transparent,
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // Text overlay at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Title
            Text(
                text = novelFileInfo.title.cleanBookTitle().truncateMiddle(30),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                color = androidx.compose.ui.graphics.Color.White
            )

            // File info and progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileInfoText,
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                )

                if (progressText.isNotEmpty()) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorAccent
                    )
                }

                // Actions row
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(
                            id = if (novelFileInfo.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
                        ),
                        contentDescription = "Favorite",
                        tint = if (novelFileInfo.isFavorite) ColorAccent else androidx.compose.ui.graphics.Color.White.copy(
                            alpha = 0.8f
                        ),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onToggleFavorite(novelFileInfo) }
                    )

                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_outline_delete_24),
                        contentDescription = "Delete",
                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDelete(novelFileInfo) }
                    )
                }
            }
        }
    }
}

// Preview helper functions to create fake data
@Suppress("unused")
@Composable
private fun createFakeNovelFileInfo(
    title: String,
    author: String,
    progress: Int = 0,
    isFavorite: Boolean = false
): NovelFileInfo {
    return NovelFileInfo(
        id = title,
        path = "/storage/emulated/0/Books/$title.epub",
        directory = "/storage/emulated/0/Books",
        author = author,
        date = System.currentTimeMillis(),
        tags = listOf("Fantasy", "Adventure"),
        title = title,
        description = "A wonderful story about $title",
        chapters = emptyList(),
        progress = progress,
        currentChapter = 0,
        chapterProgress = 0
    ).apply {
        this.isFavorite = isFavorite
    }
}

private fun createFakeBrowseData(
    includeBooks: Boolean = true,
    includeFolders: Boolean = true
): BrowseData {
    val browsableItems = mutableListOf<Browsable>()

    // Add folders
    if (includeFolders) {
        browsableItems.add(Browsable(File("/storage/emulated/0/Books/Fantasy")))
        browsableItems.add(Browsable(File("/storage/emulated/0/Books/SciFi")))
        browsableItems.add(Browsable(File("/storage/emulated/0/Books/Romance")))
    }

    // Add books
    if (includeBooks) {
        browsableItems.add(
            Browsable(
                File("/storage/emulated/0/Books/The Great Adventure.epub"),
                NovelFileInfo(
                    id = "1",
                    path = "/storage/emulated/0/Books/The Great Adventure.epub",
                    directory = "/storage/emulated/0/Books",
                    author = "John Doe",
                    date = System.currentTimeMillis(),
                    tags = listOf("Fantasy", "Adventure"),
                    title = "The Great Adventure",
                    description = "An epic journey",
                    chapters = emptyList(),
                    progress = 45,
                    currentChapter = 5,
                    chapterProgress = 20
                ).apply { isFavorite = true }
            )
        )

        browsableItems.add(
            Browsable(
                File("/storage/emulated/0/Books/Mystery Novel.epub"),
                NovelFileInfo(
                    id = "2",
                    path = "/storage/emulated/0/Books/Mystery Novel.epub",
                    directory = "/storage/emulated/0/Books",
                    author = "Jane Smith",
                    date = System.currentTimeMillis(),
                    tags = listOf("Mystery", "Thriller"),
                    title = "Mystery Novel",
                    description = "A thrilling mystery",
                    chapters = emptyList(),
                    progress = 0,
                    currentChapter = 0,
                    chapterProgress = 0
                ).apply { isFavorite = false }
            )
        )

        browsableItems.add(
            Browsable(
                File("/storage/emulated/0/Books/Space Odyssey.epub"),
                NovelFileInfo(
                    id = "3",
                    path = "/storage/emulated/0/Books/Space Odyssey.epub",
                    directory = "/storage/emulated/0/Books",
                    author = "Arthur Clarke",
                    date = System.currentTimeMillis(),
                    tags = listOf("SciFi", "Space"),
                    title = "Space Odyssey",
                    description = "Journey through space",
                    chapters = emptyList(),
                    progress = 80,
                    currentChapter = 15,
                    chapterProgress = 50
                ).apply { isFavorite = true }
            )
        )
    }

    val parents = listOf(
        File("/storage/emulated/0"),
        File("/storage/emulated/0/Books")
    )

    return BrowseData(browsableItems, parents)
}

// Preview: List view with mixed content (folders and books)
@Preview(name = "List View - Mixed Content", showBackground = true)
@Composable
fun FilesListViewPreview() {
    MaterialTheme {
        Files(
            viewing = Viewing.LIST,
            browseData = createFakeBrowseData(),
            onBookClick = {},
            folderClicked = {}
        )
    }
}

// Preview: Grid view with mixed content
@Preview(name = "Grid View - Mixed Content", showBackground = true)
@Composable
fun FilesGridViewPreview() {
    MaterialTheme {
        Files(
            viewing = Viewing.GRID,
            browseData = createFakeBrowseData(),
            onBookClick = {},
            folderClicked = {}
        )
    }
}

// Preview: Dark theme - List view
@Preview(
    name = "List View - Dark Theme",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun FilesListViewDarkPreview() {
    MaterialTheme {
        Files(
            viewing = Viewing.LIST,
            browseData = createFakeBrowseData(),
            onBookClick = {},
            folderClicked = {}
        )
    }
}

// Preview: Dark theme - Grid view
@Preview(
    name = "Grid View - Dark Theme",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun FilesGridViewDarkPreview() {
    MaterialTheme {
        Files(
            viewing = Viewing.GRID,
            browseData = createFakeBrowseData(),
            onBookClick = {},
            folderClicked = {}
        )
    }
}

// Preview: Only folders
@Preview(name = "Only Folders - List View", showBackground = true)
@Composable
fun FilesFoldersOnlyPreview() {
    MaterialTheme {
        Files(
            viewing = Viewing.LIST,
            browseData = createFakeBrowseData(includeBooks = false, includeFolders = true),
            onBookClick = {},
            folderClicked = {}
        )
    }
}

// Preview: Only books
@Preview(name = "Only Books - Grid View", showBackground = true)
@Composable
fun FilesBooksOnlyPreview() {
    MaterialTheme {
        Files(
            viewing = Viewing.GRID,
            browseData = createFakeBrowseData(includeBooks = true, includeFolders = false),
            onBookClick = {},
            folderClicked = {}
        )
    }
}

// Preview: Empty state
@Preview(name = "Empty State", showBackground = true)
@Composable
fun FilesEmptyPreview() {
    MaterialTheme {
        Files(
            viewing = Viewing.LIST,
            browseData = BrowseData(emptyList(), listOf(File("/storage/emulated/0/Books"))),
            onBookClick = {},
            folderClicked = {}
        )
    }
}

// Preview: Individual novel file list item
@Preview(name = "Novel File List Item", showBackground = true)
@Composable
fun NovelFileListItemPreview() {
    MaterialTheme {
        NovelFileListItem(
            novelFileInfo = NovelFileInfo(
                id = "1",
                path = "/storage/emulated/0/Books/Sample Novel.epub",
                directory = "/storage/emulated/0/Books",
                author = "Sample Author",
                date = System.currentTimeMillis(),
                tags = listOf("Fantasy"),
                title = "Sample Novel with a Very Long Title That Should Be Truncated",
                description = "A sample description",
                chapters = emptyList(),
                progress = 65,
                currentChapter = 10,
                chapterProgress = 30
            ).apply { isFavorite = true },
            onBookClick = {}
        )
    }
}

// Preview: Individual novel file grid item
@Preview(name = "Novel File Grid Item", showBackground = true)
@Composable
fun NovelFileGridItemPreview() {
    MaterialTheme {
        NovelFileGridItem(
            novelFileInfo = NovelFileInfo(
                id = "1",
                path = "/storage/emulated/0/Books/Sample Novel.epub",
                directory = "/storage/emulated/0/Books",
                author = "Sample Author",
                date = System.currentTimeMillis(),
                tags = listOf("Fantasy"),
                title = "Sample Novel",
                description = "A sample description",
                chapters = emptyList(),
                progress = 25,
                currentChapter = 5,
                chapterProgress = 10
            ).apply { isFavorite = false },
            onBookClick = {}
        )
    }
}

// Preview: Folder list item
@Preview(name = "Folder List Item", showBackground = true)
@Composable
fun FolderListItemPreview() {
    MaterialTheme {
        FolderListItem(
            directory = File("/storage/emulated/0/Books/Fantasy"),
            onFolderClick = {}
        )
    }
}

// Preview: Folder grid item
@Preview(name = "Folder Grid Item", showBackground = true)
@Composable
fun FolderGridItemPreview() {
    MaterialTheme {
        FolderGridItem(
            directory = File("/storage/emulated/0/Books/SciFi"),
            onFolderClick = {}
        )
    }
}

// Preview: Files header with breadcrumbs
@Preview(name = "Files Header", showBackground = true)
@Composable
fun FilesHeaderPreview() {
    MaterialTheme {
        FilesHeader(
            parents = listOf(
                File("/storage/emulated/0"),
                File("/storage/emulated/0/Documents"),
                File("/storage/emulated/0/Documents/Books")
            ),
            parentClicked = {}
        )
    }
}

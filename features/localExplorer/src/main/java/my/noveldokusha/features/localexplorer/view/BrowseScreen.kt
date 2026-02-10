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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aliyounes.aurui.components.AurErrorView
import com.aliyounes.aurui.components.AurErrorType
import my.noveldoksuha.coreui.components.Loading
import my.noveldoksuha.coreui.components.LoadingSnackbar
import my.noveldoksuha.coreui.components.NoData
import my.noveldoksuha.coreui.components.OnLifecycleEvent
import my.noveldokusha.ui.composeViews.SearchBar
import my.noveldoksuha.coreui.components.Viewing

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
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
            ) {
                CircularProgressIndicator()
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            SearchBar(
                modifier = Modifier.weight(1f, false),
                query = viewModel.query.value,
                onQueryChange = {
                    viewModel.setSearchText(it)
                    if (it.isEmpty()) {
                        viewModel.clearSearch()
                    } else {
                        viewModel.search() // This now uses debounced search
                    }
                },
                onDone = { viewModel.search() },
                onSearchFocusChange = viewModel::setSearchFocused,
                onClearQuery = { viewModel.clearSearch() },
                onBack = { viewModel.clearSearch() },
                focused = viewModel.searchFocused.value
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
                    Files(viewing = viewing, browseData = uiState.data, onBookClick = { file ->
                        showDialog = true
                        onBookClick(file)
                    }) {
                        viewModel.browse(it)
                    }
                }

                is BrowseScreenState.Error -> {
                    uiState.error.printStackTrace()
                    AurErrorView(
                        errorType = AurErrorType.Custom,
                        title = "Browse Error",
                        message = "Unable to browse directory",
                        onRetry = {
                            viewModel.browse(FileManager.getCurrentDirectory())
                        },
                        retryText = "Try Again"
                    )
                }

                is BrowseScreenState.Loading -> {
                    if (uiState.data?.browsable.isNullOrEmpty())
                        Loading()
                    else
                        Files(viewing = viewing, browseData = uiState.data!!, true, { file ->
                            showDialog = true
                            onBookClick(file)
                        }) {
                            viewModel.browse(it)
                        }
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
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
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
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (content.isNotEmpty()) {
                Text(
                    text = content,
                    fontSize = 12.sp,
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
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
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
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (content.isNotEmpty()) {
                Text(
                    text = content,
                    fontSize = 12.sp,
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
    folderClicked: (file: File) -> Unit
) {
    val snackbarHostState = remember {
        SnackbarHostState()
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
                            modifier = Modifier.padding(8.dp)
                        ) {
                            items(
                                count = browseData.browsable.size,
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
                                        onBookClick = onBookClick
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
                                        onBookClick = onBookClick
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

    Column(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(4.dp)
            )
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // First row with home icon and root
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable { parentClicked(FileManager.getStorageRoot()!!) },
                painter = painterResource(id = R.drawable.ic_home_black_24dp),
                contentDescription = "Home",
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Storage Root",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { parentClicked(FileManager.getStorageRoot()!!) }
            )
        }

        // Path breadcrumbs - show all parents in a flowable layout
        if (parents.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(parents.size) { index ->
                    val parent = parents[index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.baseline_chevron_right_24),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = parent.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clickable { parentClicked.invoke(parent) }
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Current directory
        val currentDir = FileManager.getCurrentDirectory()
        if (currentDir != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.baseline_chevron_right_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = currentDir.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun NovelFileListItem(novelFileInfo: NovelFileInfo, onBookClick: (file: File) -> Unit) {
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
            .clickable { onBookClick(File(novelFileInfo.path)) }
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover image with placeholder
        novelCover?.bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = novelCover!!.text,
                modifier = Modifier
                    .size(width = 72.dp, height = 100.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } ?: novelCover?.vector?.let { id ->
            Image(
                imageVector = ImageVector.vectorResource(id),
                contentDescription = novelCover!!.text,
                modifier = Modifier
                    .size(width = 72.dp, height = 100.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 100.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = novelFileInfo.title.truncateMiddle(50),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = novelFileInfo.author,
                fontSize = 12.sp,
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
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (progressText.isNotEmpty()) {
                    Text(
                        text = progressText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        // Actions
        Row {
            Icon(
                imageVector = ImageVector.vectorResource(
                    id = if (novelFileInfo.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
                ),
                contentDescription = "Favorite",
                tint = if (novelFileInfo.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.6f
                ),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { /* Handle favorite toggle */ }
                    .padding(4.dp)
            )

            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_outline_delete_24),
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { /* Handle delete */ }
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun NovelFileGridItem(novelFileInfo: NovelFileInfo, onBookClick: (file: File) -> Unit) {
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
            .clip(RoundedCornerShape(8.dp))
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
                    strokeWidth = 3.dp
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
                text = novelFileInfo.title.truncateMiddle(30),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
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
                    fontSize = 10.sp,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                )

                if (progressText.isNotEmpty()) {
                    Text(
                        text = progressText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
                        tint = if (novelFileInfo.isFavorite) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White.copy(
                            alpha = 0.8f
                        ),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { /* Handle favorite toggle */ }
                    )

                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_outline_delete_24),
                        contentDescription = "Delete",
                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { /* Handle delete */ }
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

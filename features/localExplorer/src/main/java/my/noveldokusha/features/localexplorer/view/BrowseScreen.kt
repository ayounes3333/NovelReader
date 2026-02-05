package my.noveldoksha.features.localexplorer.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
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
                contentAlignment= Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
            ) {
                CircularProgressIndicator()
            }
        }
    }

    OnLifecycleEvent { _, event ->
        when(event) {
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
    val isEmpty = remember(directory) {
        directory.listFiles()?.isEmpty() ?: true
    }
    val content = remember(directory) { directory.content }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(8.dp)
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
            Text(
                text = content,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun FolderGridItem(
    modifier: Modifier = Modifier,
    directory: File,
    onFolderClick: (directory: File) -> Unit = {}
) {
    val isEmpty = remember(directory) {
        directory.listFiles()?.isEmpty() ?: true
    }
    val content = remember(directory) { directory.content }

    ConstraintLayout(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth()
            .defaultMinSize(minWidth = 142.dp, minHeight = 250.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .clickable {
                if (directory.isDirectory)
                    onFolderClick(directory)
            }
    ) {
        val (icon, content) = createRefs()
        Icon(
            modifier = Modifier
                .size(72.dp)
                .padding(4.dp)
                .constrainAs(icon) {
                    linkTo(
                        top = parent.top,
                        topMargin = 8.dp,
                        bottom = content.top,
                        bottomMargin = 8.dp
                    )
                    linkTo(
                        start = parent.start,
                        startMargin = 8.dp,
                        end = parent.end,
                        endMargin = 8.dp
                    )
                },
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(8.dp)
                .constrainAs(content) {
                    bottom.linkTo(parent.bottom, margin = 8.dp)
                    start.linkTo(parent.start, margin = 8.dp)
                    end.linkTo(parent.end, margin = 8.dp)
                }
        ) {
            Text(
                text = directory.name,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = directory.content,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun Files(viewing: Viewing, browseData: BrowseData, isLoading: Boolean = false, onBookClick: (file: File) -> Unit, folderClicked: (file: File) -> Unit) {
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
            .background(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(4.dp))
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
    // Cache expensive computations
    val novelCover = remember(novelFileInfo.path) { novelFileInfo.cover.invoke() }
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
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover image
        Image(
            bitmap = novelCover.bitmap.asImageBitmap(),
            contentDescription = novelCover.text,
            modifier = Modifier
                .size(width = 72.dp, height = 100.dp)
                .clip(RoundedCornerShape(4.dp))
        )

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
                tint = if (novelFileInfo.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
    // Cache expensive computations
    val novelCover = remember(novelFileInfo.path) { novelFileInfo.cover.invoke() }
    val fileInfoText = remember(novelFileInfo.path) {
        "${novelFileInfo.dateFormatted} ${novelFileInfo.fileSize}"
    }
    val progressText = remember(novelFileInfo.progress) {
        if (novelFileInfo.progress > 0) "${novelFileInfo.progress}%" else ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBookClick(File(novelFileInfo.path)) }
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cover image
        Image(
            bitmap = novelCover.bitmap.asImageBitmap(),
            contentDescription = novelCover.text,
            modifier = Modifier
                .size(width = 120.dp, height = 160.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        // Title
        Text(
            text = novelFileInfo.title.truncateMiddle(30),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )

        // File info and progress
        Column(
            modifier = Modifier.padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = fileInfoText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            if (progressText.isNotEmpty()) {
                Text(
                    text = progressText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Actions row
        Row(
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(
                    id = if (novelFileInfo.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
                ),
                contentDescription = "Favorite",
                tint = if (novelFileInfo.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(20.dp)
                    .clickable { /* Handle favorite toggle */ }
                    .padding(2.dp)
            )

            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_outline_delete_24),
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(20.dp)
                    .clickable { /* Handle delete */ }
                    .padding(2.dp)
            )
        }
    }
}

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun BrowsePreview() {

}

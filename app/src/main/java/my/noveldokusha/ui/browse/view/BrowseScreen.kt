package my.noveldokusha.ui.browse.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import my.noveldokusha.R
import my.noveldokusha.ui.composeViews.ErrorState
import my.noveldokusha.ui.composeViews.Loading
import my.noveldokusha.ui.composeViews.LoadingSnackbar
import my.noveldokusha.ui.composeViews.NoData
import my.noveldokusha.ui.composeViews.SearchBar
import my.noveldokusha.ui.composeViews.Viewing
import my.noveldokusha.ui.browse.BrowseScreenState
import my.noveldokusha.ui.browse.FileManager
import my.noveldokusha.ui.browse.extractor.data.NovelFileInfo
import my.noveldokusha.ui.browse.extractor.utils.content
import my.noveldokusha.ui.browse.viewmodel.BrowseData
import my.noveldokusha.ui.browse.viewmodel.BrowseViewModel
import my.noveldokusha.ui.theme.colorApp
import my.noveldokusha.utils.OnLifecycleEvent
import java.io.File

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
        }
    }
    
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            SearchBar(
                modifier = Modifier.weight(1f, false),
                query = viewModel.query.value,
                onQueryChange = {
                    viewModel.setSearchText(it)
                    viewModel.search()
                    if (it.isEmpty()) {
                        viewModel.clearSearch()
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
                    ErrorState(
                        otherActionText = "Go Back",
                        otherAction = {
                            val destination: File? = FileManager.getCurrentDirectory()?.parentFile ?: FileManager.getStorageRoot()
                            FileManager.goTo(destination)
                        },
                        retry =  {
                            viewModel.browse(FileManager.getCurrentDirectory())
                        }
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorApp.bookSurface, RoundedCornerShape(8.dp))
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
                    id = if (directory.listFiles().isNullOrEmpty())
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
                text = directory.content,
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
    ConstraintLayout(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth()
            .defaultMinSize(minWidth = 142.dp, minHeight = 250.dp)
            .background(MaterialTheme.colorApp.bookSurface, RoundedCornerShape(8.dp))
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
                    id = if (directory.listFiles().isNullOrEmpty())
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
                Row {
                    if (viewing == Viewing.LIST) {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            items(count = browseData.browsable.size) { index ->
                                if (browseData.browsable[index].isDirectory)
                                    FolderListItem(
                                        directory = browseData.browsable[index].file,
                                        onFolderClick = folderClicked
                                    )
                                else
                                    NovelFileListItem(novelFileInfo = browseData.browsable[index].novelFileInfo!!, onBookClick)
                            }
                        }
                    } else if (viewing == Viewing.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(5.dp),
                            content = {
                                items(count = browseData.browsable.size) { index ->
                                    if (browseData.browsable[index].isDirectory)
                                        FolderGridItem(
                                            directory = browseData.browsable[index].file,
                                            onFolderClick = folderClicked
                                        )
                                    else
                                        NovelFileGridItem(novelFileInfo = browseData.browsable[index].novelFileInfo!!, onBookClick)
                                }
                            })
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
    Box(
        modifier = Modifier
            .background(color = MaterialTheme.colorApp.tabSurface, shape = RoundedCornerShape(4.dp))
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(5.dp)
        ) {
            Icon(modifier = Modifier.padding(5.dp).clickable {
                 parentClicked(FileManager.getStorageRoot()!!)
            }, painter = painterResource(id = R.drawable.ic_home_black_24dp), contentDescription = "Home")
            Text(
                text = if (parents.size > 3) ".../" else "/",
                color = MaterialTheme.colorScheme.onSurface
            )
            parents.takeLast(3).forEach { parent ->
                Text(
                    text = parent.name.take(12) + if (parent.name.length > 12) "..." else "",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(5.dp)
                        .clickable { parentClicked.invoke(parent) }
                )
                Text(text = "/", color = MaterialTheme.colorScheme.onSurface)
            }
            val current = FileManager.getCurrentDirectory()?.name ?: ""
            Text(
                text = current.take(12) + if (current.length > 12) "..." else "",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(5.dp)
            )
        }
    }
}

@Composable
fun NovelFileListItem(novelFileInfo: NovelFileInfo, onBookClick: (file: File) -> Unit) {
    ConstraintLayout (modifier = Modifier
        .clickable {
            onBookClick(File(novelFileInfo.path))
        }
        .fillMaxWidth()) {
        val (cover, title, author, fileInfo, favorite, options, progress) = createRefs()
        Image(
            bitmap = novelFileInfo.cover.bitmap.asImageBitmap(),
            contentDescription = novelFileInfo.cover.text,
            modifier = Modifier
                .size(width = 72.dp, height = 100.dp)
                .constrainAs(cover) {
                    top.linkTo(parent.top, margin = 5.dp)
                    start.linkTo(parent.start, margin = 5.dp)
                }
        )
        Text(
            maxLines = 3,
            text = novelFileInfo.title,
            fontSize = 16.sp,
            modifier = Modifier
                .constrainAs(title) {
                    top.linkTo(parent.top, margin = 8.dp)
                    start.linkTo(cover.end, margin = 8.dp)
                    end.linkTo(parent.end, margin = 8.dp)
                    width = Dimension.fillToConstraints
                },
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            maxLines = 1,
            text = novelFileInfo.author,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .constrainAs(author) {
                    top.linkTo(title.bottom, margin = 8.dp)
                    start.linkTo(cover.end, margin = 8.dp)
                    end.linkTo(parent.end, margin = 8.dp)
                    width = Dimension.fillToConstraints
                },
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = "${novelFileInfo.dateFormatted} ${novelFileInfo.fileSize}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.constrainAs(fileInfo) {
                linkTo(
                    top = author.bottom,
                    topMargin = 10.dp,
                    bottom = parent.bottom,
                    bottomMargin = 8.dp,
                    bias = 1.0f
                )
                start.linkTo(cover.end, margin = 8.dp)
            },
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = if (novelFileInfo.progress > 0) "${novelFileInfo.progress}%" else "",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.constrainAs(progress) {
                linkTo(
                    top = author.bottom,
                    topMargin = 10.dp,
                    bottom = parent.bottom,
                    bottomMargin = 8.dp,
                    bias = 1.0f
                )
                start.linkTo(fileInfo.end, margin = 8.dp)
            },
            color = MaterialTheme.colorScheme.onPrimary
        )
        Icon(
            imageVector = ImageVector.vectorResource(id = if (novelFileInfo.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
            contentDescription = "isFavorite",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(24.dp)
                .clickable { }
                .constrainAs(favorite) {
                    linkTo(
                        top = author.bottom,
                        topMargin = 10.dp,
                        bottom = parent.bottom,
                        bottomMargin = 8.dp,
                        bias = 1.0f
                    )
                    start.linkTo(progress.end, margin = 8.dp)
                }
        )
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_outline_delete_24),
            tint = MaterialTheme.colorScheme.onPrimary,
            contentDescription = "Reset",
            modifier = Modifier
                .size(24.dp)
                .clickable { }
                .constrainAs(options) {
                    linkTo(
                        top = author.bottom,
                        topMargin = 10.dp,
                        bottom = parent.bottom,
                        bottomMargin = 8.dp,
                        bias = 1.0f
                    )
                    linkTo(
                        start = favorite.end,
                        end = parent.end,
                        startMargin = 8.dp,
                        endMargin = 8.dp,
                        bias = 1.0f
                    )
                }
        )
    }
}

@Composable
fun NovelFileGridItem(novelFileInfo: NovelFileInfo, onBookClick: (file: File) -> Unit) {
    ConstraintLayout (
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBookClick(File(novelFileInfo.path)) }
            .padding(8.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorApp.bookSurface, RoundedCornerShape(8.dp))) {
        val (cover, title, fileInfo, favorite, options, progress) = createRefs()
        Image(
            bitmap = novelFileInfo.cover.bitmap.asImageBitmap(),
            contentDescription = novelFileInfo.cover.text,
            modifier = Modifier
                .size(width = 142.dp, height = 200.dp)
                .constrainAs(cover) {
                    top.linkTo(parent.top, margin = 5.dp)
                    start.linkTo(parent.start, margin = 5.dp)
                    end.linkTo(parent.end, margin = 5.dp)
                }
        )
        Text(
            maxLines = 3,
            text = novelFileInfo.title,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .constrainAs(title) {
                    top.linkTo(cover.bottom, margin = 5.dp)
                    start.linkTo(parent.start, margin = 8.dp)
                    end.linkTo(parent.end, margin = 8.dp)
                    width = Dimension.fillToConstraints
                },
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${novelFileInfo.dateFormatted} ${novelFileInfo.fileSize}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.constrainAs(fileInfo) {
                top.linkTo(title.bottom, margin = 5.dp)
                bottom.linkTo(parent.bottom, margin = 5.dp)
                start.linkTo(parent.start, margin = 8.dp)
            },
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (novelFileInfo.progress > 0) "${novelFileInfo.progress}%" else "",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.constrainAs(progress) {
                top.linkTo(title.bottom, margin = 5.dp)
                bottom.linkTo(parent.bottom, margin = 5.dp)
                start.linkTo(fileInfo.end, margin = 5.dp)
            },
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = ImageVector.vectorResource(id = if (novelFileInfo.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
            contentDescription = "isFavorite",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(24.dp)
                .clickable { }
                .constrainAs(favorite) {
                    top.linkTo(title.bottom, margin = 5.dp)
                    bottom.linkTo(parent.bottom, margin = 5.dp)
                    start.linkTo(progress.end, margin = 5.dp)
                }
        )
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_outline_delete_24),
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = "Reset",
            modifier = Modifier
                .size(24.dp)
                .clickable { }
                .constrainAs(options) {
                    top.linkTo(title.bottom, margin = 5.dp)
                    bottom.linkTo(parent.bottom, margin = 5.dp)
                    linkTo(
                        start = favorite.end,
                        end = parent.end,
                        startMargin = 5.dp,
                        endMargin = 8.dp,
                        bias = 1.0f
                    )
                }
        )
    }
}

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun BrowsePreview() {

}


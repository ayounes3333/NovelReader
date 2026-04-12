package my.noveldoksuha.coreui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import my.noveldoksuha.coreui.R
import my.noveldoksuha.coreui.theme.InternalTheme

@Composable
fun Loading(
    loadingColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = loadingColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Loading...",
                color = textColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LoadingPreview() {
    InternalTheme {
        Loading()
    }
}

@Composable
fun LoadingSnackbar(
    modifier: Modifier,
    snackbarHostState: SnackbarHostState,
    loadingColor: Color = MaterialTheme.colorScheme.inverseOnSurface,
    textColor: Color = MaterialTheme.colorScheme.inverseOnSurface
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier.background(MaterialTheme.colorScheme.inverseSurface),
        snackbar = { snackbarData ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier.align(Alignment.CenterStart),
                    text = snackbarData.visuals.message,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterEnd),
                    color = loadingColor,
                    strokeWidth = 2.dp
                )
            }
        }
    )

}

@Composable
fun NoData(
    @DrawableRes
    iconRes: Int = R.drawable.ic_logo_foreground,
    iconColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    message: String = "No data found!"
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                modifier = Modifier.size(64.dp),
                imageVector = ImageVector.vectorResource(id = iconRes),
                contentDescription = "empty",
                tint = iconColor
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                text = message,
                textAlign = TextAlign.Center,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ErrorState(
    iconColor: Color = MaterialTheme.colorScheme.error,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    canRetry: Boolean = true,
    otherActionText: String = "",
    otherAction: () -> Unit = {},
    message: String = "Something went wrong! Please try again later.",
    retry: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                modifier = Modifier.size(64.dp),
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_baseline_error_outline_24),
                contentDescription = "error",
                tint = iconColor
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                text = message,
                textAlign = TextAlign.Center,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
            if (canRetry) {
                Spacer(modifier = Modifier.size(12.dp))
                Button(onClick = retry, shape = MaterialTheme.shapes.small) {
                    Text(text = "Retry")
                }
            }
            if (otherActionText.isNotEmpty()) {
                Spacer(modifier = Modifier.size(8.dp))
                Button(onClick = otherAction, shape = MaterialTheme.shapes.small) {
                    Text(text = otherActionText)
                }
            }
        }
    }
}

@Composable
fun ExpandableBox(
    isExpanded: Boolean,
    expandedBackgroundColor: Color,
    content: @Composable BoxScope.() -> Unit) {
    // Opening Animation
    val expandTransition = remember {
        expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(300)
        ) + fadeIn(
            animationSpec = tween(300)
        )
    }

    // Closing Animation
    val collapseTransition = remember {
        shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(300)
        ) + fadeOut(
            animationSpec = tween(300)
        )
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = expandTransition,
        exit = collapseTransition
    ) {
        Box(
            modifier = Modifier
                .background(expandedBackgroundColor)
                .padding(15.dp)
        ) {
            content()
        }
    }
}

sealed class AsyncCoverState {
    object LOADING : AsyncCoverState()
    class LOADED(val bitmap: Bitmap) : AsyncCoverState()
    object ERROR : AsyncCoverState()
}

@Composable
fun ToolBar(
    title: String,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onPrimary,
    titleTextSize: TextUnit = 14.sp,
    leftButtons: @Composable () -> Unit = {},
    rightButtons: @Composable () -> Unit = {},
    showLogo: Boolean = true,
    logoRes: Int = R.drawable.ic_logo_foreground,
    contents: @Composable () -> Unit = {}
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.weight(1f)
            ) {
                leftButtons()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                if (showLogo) {
                    Image(
                        modifier = Modifier.size(width = 36.dp, height = 36.dp),
                        imageVector = ImageVector.vectorResource(logoRes),
                        contentDescription = "Toolbar Logo"
                    )
                }
                if (showTitle) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = title,
                        color = titleColor,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = titleTextSize
                        )
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f)
            ) {
                rightButtons()
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            contents()
        }
    }
}
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    minimizedMaxLines: Int = 1,
) {
    var cutText by remember(text) { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val textLayoutResultState = remember { mutableStateOf<TextLayoutResult?>(null) }
    val seeMoreSizeState = remember { mutableStateOf<IntSize?>(null) }
    val seeMoreOffsetState = remember { mutableStateOf<Offset?>(null) }

    // getting raw values for smart cast
    val textLayoutResult = textLayoutResultState.value
    val seeMoreSize = seeMoreSizeState.value
    val seeMoreOffset = seeMoreOffsetState.value

    LaunchedEffect(text, expanded, textLayoutResult, seeMoreSize) {
        val lastLineIndex = minimizedMaxLines - 1
        if (!expanded && textLayoutResult != null && seeMoreSize != null
            && lastLineIndex + 1 == textLayoutResult.lineCount
            && textLayoutResult.isLineEllipsized(lastLineIndex)
        ) {
            var lastCharIndex = textLayoutResult.getLineEnd(lastLineIndex, visibleEnd = true) + 1
            var charRect: Rect
            do {
                lastCharIndex -= 1
                charRect = textLayoutResult.getCursorRect(lastCharIndex)
            } while (
                charRect.left > textLayoutResult.size.width - seeMoreSize.width
            )
            seeMoreOffsetState.value = Offset(charRect.left, charRect.bottom - seeMoreSize.height)
            cutText = text.substring(startIndex = 0, endIndex = lastCharIndex)
        }
    }

    Box(modifier) {
        Text(
            text = cutText ?: text,
            maxLines = if (expanded) Int.MAX_VALUE else minimizedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResultState.value = it },
        )
        if (!expanded) {
            val density = LocalDensity.current
            Text(
                "... See more",
                onTextLayout = { seeMoreSizeState.value = it.size },
                modifier = Modifier
                    .then(
                        if (seeMoreOffset != null)
                            Modifier.offset(
                                x = with(density) { seeMoreOffset.x.toDp() },
                                y = with(density) { seeMoreOffset.y.toDp() },
                            )
                        else
                            Modifier
                    )
                    .clickable {
                        expanded = true
                        cutText = null
                    }
                    .alpha(if (seeMoreOffset != null) 1f else 0f)
            )
        }
    }
}

@Preview(showBackground = false, showSystemUi = true)
@Composable
fun DefaultPreview() {

        ToolBar(
            title = "107509"
        ) {
            var focused by remember { mutableStateOf(false) }
            my.noveldokusha.ui.composeViews.SearchBar(
                query = "",
                onQueryChange = {},
                onSearchFocusChange = { focused = it },
                onClearQuery = {  },
                onDone = {  },
                onBack = {  },
                focused = focused
            )
        }
}
@Composable
fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event ->
            eventHandler.value(owner, event)
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

package my.noveldokusha.ui.composeViews

import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.noveldokusha.R
import my.noveldokusha.ui.theme.InternalTheme

@Composable
fun Loading(
    loadingColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = loadingColor)
            Row {
                Text(text = "Loading...", color = textColor, fontSize = 12.sp)
            }
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
    loadingColor: Color = Color.White,
    textColor: Color = Color.White
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier.background(Color.DarkGray),
        snackbar = { snackbarData ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Text(modifier = Modifier.align(Alignment.CenterStart), text = snackbarData.visuals.message, color = textColor)
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterEnd), color = loadingColor)
            }
        }
    )

}

@Composable
fun NoData(
    @DrawableRes
    iconRes: Int = R.drawable.ic_logo_foreground,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    message: String = "No data found!"
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                modifier = Modifier.size(32.dp),
                imageVector = ImageVector.vectorResource(id = iconRes),
                contentDescription = "empty",
                tint = iconColor
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row {
                Text(
                    modifier = Modifier.padding(horizontal = 96.dp),
                    text = message,
                    textAlign = TextAlign.Center,
                    color = textColor,
                    fontSize = 14.sp
                )
            }
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
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(modifier =Modifier.size(32.dp), imageVector = ImageVector.vectorResource(id = R.drawable.ic_baseline_error_outline_24), contentDescription = "error", tint = iconColor)
            Spacer(modifier = Modifier.size(8.dp))
            Row {
                Text(modifier = Modifier.padding(horizontal = 96.dp), text = message, textAlign = TextAlign.Center, color = textColor, fontSize = 14.sp)
            }
            if (canRetry) {
                Spacer(modifier = Modifier.size(8.dp))
                Row {
                    Button(onClick = retry, shape = RoundedCornerShape(8.dp)) {
                        Text(text = "Retry", color = Color.White)
                    }
                }
            }
            if (otherActionText.isNotEmpty()) {
                Spacer(modifier = Modifier.size(8.dp))
                Row {
                    Button(onClick = otherAction, shape = RoundedCornerShape(8.dp)) {
                        Text(text = otherActionText, color = Color.White)
                    }
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
        Row {
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
                modifier = Modifier.weight(1f)) {
                if (showLogo) {
                    Image(
                        modifier = Modifier
                            .size(
                                width = 40.dp,
                                height = 40.dp
                            ),
                        imageVector = ImageVector.vectorResource(logoRes),
                        contentDescription = "Toolbar Logo"
                    )
                }
                if (showTitle) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = titleColor,
                            fontSize = titleTextSize
                        )
                    }
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
        Box(modifier = Modifier.fillMaxWidth())  {
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
            SearchBar(
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
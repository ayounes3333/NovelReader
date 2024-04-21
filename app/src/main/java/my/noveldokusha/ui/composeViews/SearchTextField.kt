package my.noveldokusha.ui.composeViews

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import my.noveldokusha.R


@Composable
private fun SearchHint(
    modifier: Modifier = Modifier,
    hint: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)

    ) {
        Text(
            color = Color(0xff757575),
            text = hint,
        )
    }
}

/**
 * This is a stateless TextField for searching with a Hint when query is empty,
 * and clear and loading [IconButton]s to clear query or show progress indicator when
 * a query is in progress.
 */
@Composable
fun SearchTextField(
    modifier: Modifier = Modifier,
    query: String,
    hint: String = "Search",
    onQueryChange: (String) -> Unit,
    onDone: () -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onClearQuery: () -> Unit,
    focused: Boolean,
) {
    Surface(
        modifier = modifier
            .then(
                Modifier
                    .height(56.dp)
                    .padding(
                        top = 8.dp,
                        bottom = 8.dp,
                        start = if (!focused) 16.dp else 8.dp,
                        end = if (!focused) 16.dp else 8.dp
                    )
            ),
        color = Color(0xffF5F5F5),
        shape = RoundedCornerShape(percent = 50),
    ) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                SearchHint(
                    modifier = modifier.padding(start = 24.dp, end = 8.dp),
                    hint = hint
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onSearchFocusChange(it.isFocused) }
                    .padding(top = 9.dp, bottom = 8.dp, start = 24.dp, end = 24.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onDone()
                    }
                )
            )
            if (focused && query.isNotEmpty()) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_baseline_close_24),
                    contentDescription = "Clear",
                    tint = Color.DarkGray,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(8.dp)
                        .align(Alignment.CenterEnd)
                        .clickable { onClearQuery() }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onClearQuery: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    focused: Boolean,
) {

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchTextField(
            query = query,
            onQueryChange = onQueryChange,
            onSearchFocusChange = onSearchFocusChange,
            onClearQuery = onClearQuery,
            focused = focused,
            onDone = onDone,
            modifier = modifier.weight(1f)
        )
        AnimatedVisibility(visible = focused) {
            // Back button
            Text(
                text = stringResource(id = R.string.cancel),
                color = Color.White,
                modifier = Modifier
                    .padding(
                        top = 5.dp,
                        bottom = 5.dp,
                        start = 8.dp,
                        end = 16.dp
                    )
                    .clickable {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onBack()
                    })
        }
    }
}
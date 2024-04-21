package my.noveldokusha.ui.composeViews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.R

enum class Viewing {
    LIST, GRID
}

@Composable
fun Viewing(
    modifier: Modifier = Modifier,
    tint: Color,
    viewing: Viewing,
    onViewingChange: (Viewing) -> Unit
) {
    val viewingState = remember {
        mutableStateOf(viewing)
    }
    Box(modifier = modifier.size(32.dp)) {
        Icon(
            modifier = Modifier
                .align(Alignment.Center)
                .clickable {
                    viewingState.value = if (viewingState.value == Viewing.LIST)
                        Viewing.GRID
                    else
                        Viewing.LIST
                    onViewingChange.invoke(viewingState.value)
                },
            imageVector = ImageVector.vectorResource(
                id =
                when (viewingState.value) {
                    Viewing.LIST -> R.drawable.ic_viewing_grid_24
                    Viewing.GRID -> R.drawable.ic_viewing_list_24
                }
            ),
            tint = tint,
            contentDescription = ""
        )
    }
}
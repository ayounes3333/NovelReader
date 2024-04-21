package my.noveldokusha.ui.composeViews

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.Text
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

enum class Sorting {
    NAME_ASC,
    NAME_DES,
    SIZE_ASC,
    SIZE_DES,
    TYPE_ASC,
    TYPE_DES,
    DATE_ASC,
    DATE_DES;

    val isAscendingOrder: Boolean
        get() = this == NAME_ASC ||
                this == SIZE_ASC ||
                this == TYPE_ASC ||
                this == DATE_ASC

    val sortIconRes: Int
        get() = when(this) {
            NAME_ASC,
            NAME_DES -> R.drawable.ic_sort_by_alpha_24
            SIZE_ASC,
            SIZE_DES -> R.drawable.ic_data_24
            TYPE_ASC,
            TYPE_DES -> R.drawable.ic_file_24
            DATE_ASC,
            DATE_DES -> R.drawable.ic_date_24
        }

    val sortingName: String
        get() = when(this) {
            NAME_ASC -> "By Name Ascending"
            NAME_DES -> "By Name Descending"
            SIZE_ASC -> "By Size Ascending"
            SIZE_DES -> "By Size Descending"
            TYPE_ASC -> "By Type Ascending"
            TYPE_DES -> "By Type Descending"
            DATE_ASC -> "By Date Ascending"
            DATE_DES -> "By Date Descending"
        }
}

@Composable
fun Sorting(
    modifier: Modifier = Modifier,
    tint: Color,
    sorting: Sorting,
    sortingList: List<Sorting> = Sorting.values().asList(),
    onSortingChange: (Sorting) -> Unit
) {
    val sortingState = remember {
        mutableStateOf(sorting)
    }

    Box(modifier = modifier) {
        val dropDown = remember {
            mutableStateOf(false)
        }
        Icon(
            modifier = Modifier
                .clickable {
                    dropDown.value = dropDown.value.not()
                }
                .padding(6.dp),
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_sort_24),
            tint = tint,
            contentDescription = ""
        )

        AnimatedVisibility(visible = dropDown.value) {
            LazyColumn(verticalArrangement = Arrangement.Center , modifier = Modifier.align(Alignment.TopEnd)) {
                items(sortingList.size, key = { sortingList[it].ordinal }) { index ->
                    Row(Modifier.clickable {
                        dropDown.value = false
                        onSortingChange.invoke(sortingList[index])
                    }) {
                       Icon(
                           modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                           imageVector = ImageVector.vectorResource(id = sortingList[index].sortIconRes),
                           contentDescription = ""
                       )
                        Text(modifier = Modifier.padding(end = 32.dp), text = sortingList[index].sortingName)
                    }
                }
            }
        }
    }
}
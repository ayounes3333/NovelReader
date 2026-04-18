package my.noveldokusha.libraryexplorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import my.noveldoksuha.coreui.BaseViewModel
import my.noveldoksuha.data.AppRepository
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.core.domain.LibraryCategory
import my.noveldokusha.core.utils.toState
import my.noveldokusha.feature.local_database.BookWithContext
import javax.inject.Inject

data class BookGroup(
    val seriesTitle: String,
    val books: List<BookWithContext>,
) {
    val representative: BookWithContext
        get() = books.maxByOrNull { it.book.lastReadEpochTimeMilli } ?: books[0]
}

private val seriesSuffixRegex = Regex(
    """[\s\-–—:]+(?:vol(?:ume)?\.?\s*\d+|book\s*\d+|part\s*\d+|\d+)\.?\s*$""",
    RegexOption.IGNORE_CASE
)

fun String.extractSeriesTitle(): String {
    val stripped = replace(seriesSuffixRegex, "")
        .trimEnd { it == '-' || it == '–' || it == '—' || it == ':' || it == ' ' }
    return stripped.ifEmpty { this }
}

fun List<BookWithContext>.groupBySeries(): List<BookGroup> {
    val groups = LinkedHashMap<String, MutableList<BookWithContext>>()
    for (book in this) {
        val key = book.book.title.extractSeriesTitle()
        groups.getOrPut(key) { mutableListOf() }.add(book)
    }
    return groups.map { (key, books) -> BookGroup(key, books) }
}

@HiltViewModel
internal class LibraryPageViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val preferences: AppPreferences,
    private val toasty: Toasty,

) : BaseViewModel() {
    enum class LibraryListType {
        FAVORITES, RECENT
    }

    var isPullRefreshing by mutableStateOf(false)
    val listFavorites by createPageList(LibraryListType.FAVORITES)
    val listRecent by createPageList(LibraryListType.RECENT)

    private fun createPageList(type: LibraryListType) = appRepository.libraryBooks
        .getBooksInLibraryWithContextFlow
        .map { list ->
            if (type == LibraryListType.FAVORITES)
                list  // all books in library
            else
                list.filter { it.book.lastReadEpochTimeMilli > 0 }  // only books that have been read
        }
        .combine(preferences.LIBRARY_FILTER_READ.flow()) { list, filterRead ->
            when (filterRead) {
                TernaryState.Active -> list.filter { it.chaptersCount == it.chaptersReadCount }
                TernaryState.Inverse -> list.filter { it.chaptersCount != it.chaptersReadCount }
                TernaryState.Inactive -> list
            }
        }.combine(preferences.LIBRARY_SORT_LAST_READ.flow()) { list, sortRead ->
            when (sortRead) {
                TernaryState.Active -> list.sortedByDescending { it.book.lastReadEpochTimeMilli }
                TernaryState.Inverse -> list.sortedBy { it.book.lastReadEpochTimeMilli }
                TernaryState.Inactive -> list
            }
        }
        .toState(viewModelScope, listOf())


    private fun showLoadingSpinner() {
        viewModelScope.launch {
            // Keep for 3 seconds so the user can notice the refresh has been triggered.
            isPullRefreshing = true
            delay(3000L)
            isPullRefreshing = false
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onLibraryCategoryRefresh(libraryCategory: LibraryCategory) {
        showLoadingSpinner()
        toasty.show(R.string.updating_library_notice)
    }
}

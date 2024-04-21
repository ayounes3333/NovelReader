package my.noveldokusha.features.main.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import my.noveldokusha.AppPreferences
import my.noveldokusha.R
import my.noveldokusha.data.LibraryCategory
import my.noveldokusha.repository.AppRepository
import my.noveldokusha.ui.BaseViewModel
import my.noveldokusha.ui.Toasty
import my.noveldokusha.utils.toState
import my.noveldokusha.workers.LibraryUpdatesWorker
import javax.inject.Inject

@HiltViewModel
class LibraryPageViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val preferences: AppPreferences,
    private val toasty: Toasty,
    private val workManager: WorkManager
) : BaseViewModel() {
    enum class LibraryListType {
        FAVORITES, RECENT
    }

    var isPullRefreshing by mutableStateOf(false)
    val listFavorites by createPageList(LibraryListType.FAVORITES)
    val listRecent by createPageList(LibraryListType.RECENT)

    private fun createPageList(type: LibraryListType) = appRepository.libraryBooks
        .getBooksInLibraryWithContextFlow
        .map { it.filter { book ->
            if (type == LibraryListType.FAVORITES)
                book.book.inLibrary
            else
                true
        } }
        .combine(preferences.LIBRARY_FILTER_READ.flow()) { list, filterRead ->
            when (filterRead) {
                AppPreferences.TERNARY_STATE.active -> list.filter { it.chaptersCount == it.chaptersReadCount }
                AppPreferences.TERNARY_STATE.inverse -> list.filter { it.chaptersCount != it.chaptersReadCount }
                AppPreferences.TERNARY_STATE.inactive -> list
            }
        }.combine(preferences.LIBRARY_SORT_LAST_READ.flow()) { list, sortRead ->
            when (sortRead) {
                AppPreferences.TERNARY_STATE.active -> list.sortedByDescending { it.book.lastReadEpochTimeMilli }
                AppPreferences.TERNARY_STATE.inverse -> list.sortedBy { it.book.lastReadEpochTimeMilli }
                AppPreferences.TERNARY_STATE.inactive -> list
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

    fun onLibraryCategoryRefresh(libraryCategory: LibraryCategory) {
        showLoadingSpinner()
        toasty.show(R.string.updaing_library)

        workManager.beginUniqueWork(
            LibraryUpdatesWorker.TAGManual,
            ExistingWorkPolicy.REPLACE,
            LibraryUpdatesWorker.createManualRequest(updateCategory = libraryCategory)
        ).enqueue()
    }
}

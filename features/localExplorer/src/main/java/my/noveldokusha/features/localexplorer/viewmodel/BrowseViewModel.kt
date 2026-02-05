package my.noveldokusha.features.localexplorer.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import my.noveldokusha.features.localexplorer.BrowseScreenState
import my.noveldokusha.features.localexplorer.FileManager
import my.noveldokusha.features.localexplorer.model.Browsable
import my.noveldokusha.features.localexplorer.model.BrowseRepository
import my.noveldokusha.features.localexplorer.model.BrowseResult
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor (private val browseRepository: BrowseRepository) : ViewModel() {
    var uiState: BrowseScreenState<BrowseData> by mutableStateOf(BrowseScreenState.Loading(
        BrowseData(emptyList(), emptyList())
    ))

    private val _searchFocused = mutableStateOf(false)
    val searchFocused: State<Boolean>
        get() = _searchFocused

    private val _query = mutableStateOf("")
    val query: State<String>
        get() = _query

    private var searchJob: Job? = null

    // This function will make the textSearch value changes
    fun setSearchText(it: String) {
        if (it == _query.value)
            return
        _query.value = it
    }

    // This function will make the textSearch focus changes
    fun setSearchFocused(focused: Boolean) {
        if (focused == _searchFocused.value)
            return
        _searchFocused.value = focused
    }

    fun clearSearch() {
        _query.value = ""
        searchJob?.cancel()
        browse(FileManager.getCurrentDirectory())
    }

    fun search() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300) // Debounce for 300ms
            browse(FileManager.getCurrentDirectory(), query = query.value)
        }
    }

    init {
        browse(FileManager.getCurrentDirectory())
    }

    fun browse(directory: File?, query: String = "") {
        uiState = BrowseScreenState.Loading(BrowseData(emptyList(), emptyList()))
        viewModelScope.launch {
            try {
                browseRepository.browse(directory)
                    .map { result ->
                        // Filter on background thread
                        result?.let {
                            val filtered = it.browsables.filter { file ->
                                query.isEmpty() ||
                                        file.file.name.contains(query, ignoreCase = true) ||
                                        file.novelFileInfo?.title?.contains(
                                            query,
                                            ignoreCase = true
                                        ) == true ||
                                        file.novelFileInfo?.author?.contains(
                                            query,
                                            ignoreCase = true
                                        ) == true ||
                                        file.novelFileInfo?.id?.contains(
                                            query,
                                            ignoreCase = true
                                        ) == true ||
                                        file.novelFileInfo?.tags?.joinToString(" ")
                                            ?.contains(query, ignoreCase = true) == true
                            }
                            BrowseResult(filtered, it.parents)
                        }
                    }
                    .flowOn(Dispatchers.Default) // Use Default for CPU-intensive filtering
                    .collectIndexed { index, result ->
                        result?.let {
                            if (index == 0) { //Cached
                                uiState = BrowseScreenState.Loading(
                                    BrowseData(it.browsables, it.parents)
                                )
                            } else {
                                uiState = BrowseScreenState.Data(
                                    BrowseData(it.browsables, it.parents)
                                )
                            }
                        }
                    }
            } catch (error: Exception) {
                uiState = BrowseScreenState.Error(error)
            }
        }
    }
}

data class BrowseData(val browsable: List<Browsable>, val parents: List<File>)

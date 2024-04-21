package my.noveldokusha.ui.browse.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.ui.browse.BrowseScreenState
import my.noveldokusha.ui.browse.FileManager
import my.noveldokusha.ui.browse.model.Browsable
import my.noveldokusha.ui.browse.model.BrowseRepository
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
        browse(FileManager.getCurrentDirectory())
    }

    fun search() {
        browse(FileManager.getCurrentDirectory(), query = query.value)
    }

    init {
        browse(FileManager.getCurrentDirectory())
    }

    fun browse(directory: File?, query: String = "") {
        uiState = BrowseScreenState.Loading(BrowseData(emptyList(), emptyList()))
        viewModelScope.launch(context = Dispatchers.IO) {

            try {
                browseRepository.browse(directory).collectIndexed { index, value ->
                    value?.let {
                        val filtered = value.filter { file ->
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
                                    file.novelFileInfo?.tags?.reduceOrNull { acc, s ->
                                        "$acc $s"
                                    }?.contains(query, ignoreCase = true) == true
                        }
                        if (index == 0) { //Cached
                            withContext(Dispatchers.Main) {
                                uiState = BrowseScreenState.Loading(
                                    BrowseData(filtered, browseRepository.getParentDirectories())
                                )
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                uiState = BrowseScreenState.Data(
                                    BrowseData(filtered, browseRepository.getParentDirectories())
                                )
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    uiState = BrowseScreenState.Error(error)
                }
            }
        }
    }
}

data class BrowseData(val browsable: List<Browsable>, val parents: List<File>)

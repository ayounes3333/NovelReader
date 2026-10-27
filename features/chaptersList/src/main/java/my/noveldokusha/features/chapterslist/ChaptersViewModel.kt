package my.noveldokusha.features.chapterslist

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import my.noveldoksuha.coreui.BaseViewModel
import my.noveldoksuha.data.AiBulkJob
import my.noveldoksuha.data.AiBulkJobStore
import my.noveldoksuha.data.AiRepository
import my.noveldoksuha.data.AppRepository
import my.noveldoksuha.data.DownloaderRepository
import my.noveldoksuha.data.EpubImporterRepository
import my.noveldoksuha.interactor.WorkersInteractions
import my.noveldokusha.chapterslist.R
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.Response
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.isContentUri
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.core.utils.toState
import my.noveldokusha.feature.local_database.ChapterWithContext
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.tooling.ai_engine.domain.AiTask
import javax.inject.Inject

interface ChapterStateBundle {
    val rawBookUrl: String
    val bookTitle: String
}

@HiltViewModel
internal class ChaptersViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val appScope: AppCoroutineScope,
    scraper: Scraper,
    private val toasty: Toasty,
    appPreferences: AppPreferences,
    appFileResolver: AppFileResolver,
    private val downloaderRepository: DownloaderRepository,
    private val chaptersRepository: ChaptersRepository,
    private val epubImporterRepository: EpubImporterRepository,
    private val workersInteractions: WorkersInteractions,
    private val aiRepository: AiRepository,
    private val aiBulkJobStore: AiBulkJobStore,
    stateHandle: SavedStateHandle,
) : BaseViewModel(), ChapterStateBundle {

    override val rawBookUrl by StateExtra_String(stateHandle)
    override val bookTitle by StateExtra_String(stateHandle)

    private val bookUrl = appFileResolver.getLocalIfContentType(rawBookUrl, bookFolderName = bookTitle)

    @Volatile
    private var loadChaptersJob: Job? = null

    @Volatile
    private var lastSelectedChapterUrl: String? = null
    private val source = scraper.getCompatibleSource(bookUrl)
    private val book = appRepository.libraryBooks.getFlow(bookUrl)
        .filterNotNull()
        .map(ChaptersScreenState::BookState)
        .toState(
            viewModelScope,
            ChaptersScreenState.BookState(title = bookTitle, url = bookUrl, coverImageUrl = null)
        )

    val state = ChaptersScreenState(
        book = book,
        error = mutableStateOf(""),
        chapters = mutableStateListOf(),
        selectedChaptersUrl = mutableStateMapOf(),
        isRefreshing = mutableStateOf(false),
        sourceCatalogNameStrRes = mutableStateOf(source?.nameStrId),
        settingChapterSort = appPreferences.CHAPTERS_SORT_ASCENDING.state(viewModelScope),
        isLocalSource = mutableStateOf(bookUrl.isLocalUri),
        isRefreshable = mutableStateOf(rawBookUrl.isContentUri || !bookUrl.isLocalUri),
        searchQuery = mutableStateOf(""),
        aiAvailable = mutableStateOf(false),
        aiTranslateTarget = mutableStateOf(""),
        aiChaptersWithRevision = mutableStateListOf(),
        aiRunningChapterUrl = mutableStateOf(null),
        aiBulkLatestJob = mutableStateOf(null),
        aiCustomPrompts = mutableStateListOf(),
    )

    private val _bookDeletedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val bookDeletedEvent = _bookDeletedEvent.asSharedFlow()

    init {
        appScope.launch {
            if (rawBookUrl.isContentUri && appRepository.libraryBooks.get(bookUrl) == null) {
                importUriContent()
            }
        }

        viewModelScope.launch {
            if (state.isLocalSource.value) return@launch

            if (!appRepository.bookChapters.hasChapters(bookUrl))
                updateChaptersList()

            if (appRepository.libraryBooks.get(bookUrl) != null)
                return@launch

            chaptersRepository.downloadBookMetadata(bookUrl = bookUrl, bookTitle = bookTitle)
        }

        viewModelScope.launch {
            chaptersRepository.getChaptersSortedFlow(bookUrl = bookUrl).collect {
                state.chapters.clear()
                state.chapters.addAll(it)
            }
        }

        initAiState()
    }

    private fun initAiState() {
        viewModelScope.launch {
            state.aiAvailable.value = aiRepository.isConfigured()
            state.aiTranslateTarget.value = aiRepository.translationTarget()
            state.aiCustomPrompts.clear()
            state.aiCustomPrompts.addAll(aiRepository.getCustomPrompts().map { it.id to it.name })
            refreshAiBadges()
        }
    }

    private var wasBulkJobRunning = false

    init {
        // Reactive bulk job status: the store emits on every worker update,
        // so progress and failures show up live without polling.
        viewModelScope.launch {
            aiBulkJobStore.flowOfLatestJobForBook(bookUrl).collect { job ->
                state.aiBulkLatestJob.value = job?.toSheetState()
                val running = job != null && !job.progress.finished
                if (wasBulkJobRunning && !running) refreshAiBadges()
                wasBulkJobRunning = running
            }
        }
    }

    private fun AiBulkJob.toSheetState() = AiBulkSheetJobState(
        finished = progress.finished,
        done = progress.done,
        total = progress.total,
        skipped = progress.skipped,
        currentChapterUrl = progress.currentChapterUrl
            ?.takeIf { !progress.finished },
        failures = progress.failures,
    )

    /**
     * Runs [task] over the selected chapters through the background worker.
     */
    fun runBulkAiTask(task: AiTask, regenerate: Boolean) {
        val chaptersUrl = state.selectedChaptersUrl.keys.toList()
        if (chaptersUrl.isEmpty()) return
        if (!aiRepository.isConfigured()) {
            toasty.show(R.string.ai_no_provider)
            return
        }
        enqueueBulkJob(chaptersUrl = chaptersUrl, task = task, regenerate = regenerate)
    }

    /** Re-runs the failed chapters of the latest bulk job for this book. */
    fun retryFailedBulk() {
        val job = aiBulkJobStore.getLatestJobForBook(bookUrl) ?: return
        val failedUrls = job.progress.failures.map { it.chapterUrl }
        if (failedUrls.isEmpty()) return
        val task = job.input.toTask()
        enqueueBulkJob(
            chaptersUrl = failedUrls,
            task = task,
            regenerate = true,
        )
    }

    private fun enqueueBulkJob(chaptersUrl: List<String>, task: AiTask, regenerate: Boolean) {
        val running = state.aiBulkLatestJob.value?.finished == false
        if (running) {
            toasty.show(R.string.ai_bulk_already_running)
            return
        }
        val job = aiBulkJobStore.createJob(
            bookUrl = bookUrl,
            bookTitle = bookTitle,
            chaptersUrl = chaptersUrl,
            task = task,
            model = null,
            regenerate = regenerate,
        )
        workersInteractions.runAiBulkTask(jobId = job.input.jobId)
        toasty.show(R.string.ai_bulk_started)
    }

    private suspend fun refreshAiBadges() {
        val urls = state.chapters.map { it.chapter.url }
        if (urls.isEmpty()) return
        val withRevision = aiRepository.getChaptersWithActiveRevision(urls)
        state.aiChaptersWithRevision.clear()
        state.aiChaptersWithRevision.addAll(withRevision)
    }

    /**
     * Runs a single-chapter AI task from the chapters list. Completion is
     * surfaced with a toast; the AI badge set is refreshed afterwards.
     */
    fun onChapterAiTask(chapter: ChapterWithContext, task: AiTask) {
        if (state.aiRunningChapterUrl.value != null) return
        val chapterUrl = chapter.chapter.url
        state.aiRunningChapterUrl.value = chapterUrl
        viewModelScope.launch {
            val result = aiRepository.runTask(chapterUrl = chapterUrl, task = task)
            state.aiRunningChapterUrl.value = null
            when (result) {
                is Response.Success -> toasty.show(R.string.ai_done)
                is Response.Error -> toasty.show(result.message)
            }
            refreshAiBadges()
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val isBookmarked =
                appRepository.toggleBookmark(bookTitle = bookTitle, bookUrl = bookUrl)
            val msg = if (isBookmarked) R.string.added_to_library else R.string.removed_from_library
            toasty.show(msg)
        }
    }

    fun onPullRefresh() {
        if (!state.isRefreshable.value) {
            toasty.show(R.string.local_book_nothing_to_update)
            state.isRefreshing.value = false
            return
        }
        toasty.show(R.string.updating_book_info)
        if (rawBookUrl.isContentUri) {
            importUriContent()
        } else if (!state.isLocalSource.value) {
            updateCover()
            updateDescription()
            updateChaptersList()
        }
    }

    private fun updateCover() = viewModelScope.launch {
        if (state.isLocalSource.value || book.value.coverImageUrl?.isLocalUri == true) return@launch
        downloaderRepository.bookCoverImageUrl(bookUrl = bookUrl).onSuccess {
            if (it == null) return@onSuccess
            appRepository.libraryBooks.updateCover(bookUrl, it)
        }
    }

    private fun updateDescription() = viewModelScope.launch {
        if (state.isLocalSource.value) return@launch
        downloaderRepository.bookDescription(bookUrl = bookUrl).onSuccess {
            if (it == null) return@onSuccess
            appRepository.libraryBooks.updateDescription(bookUrl, it)
        }
    }

    private fun importUriContent() {
        if (loadChaptersJob?.isActive == true) return
        loadChaptersJob = appScope.launch {
            state.error.value = ""
            state.isRefreshing.value = true
            val isInLibrary = appRepository.libraryBooks.existInLibrary(bookUrl)
            epubImporterRepository.importEpubFromContentUri(
                contentUri = rawBookUrl,
                bookTitle = bookTitle,
                addToLibrary = isInLibrary
            ).onError {
                state.error.value = it.message
            }
            state.isRefreshing.value = false
        }
    }

    private fun updateChaptersList() {
        if (loadChaptersJob?.isActive == true) return
        loadChaptersJob = appScope.launch {
            state.error.value = ""
            state.isRefreshing.value = true
            val url = bookUrl
            downloaderRepository.bookChaptersList(bookUrl = url)
                .onSuccess {
                    if (it.isEmpty())
                        toasty.show(R.string.no_chapters_found)
                    appRepository.bookChapters.merge(newChapters = it, bookUrl = url)
                }.onError {
                    state.error.value = it.message
                }
            state.isRefreshing.value = false

        }
    }

    suspend fun getLastReadChapter(): String? =
        chaptersRepository.getLastReadChapter(bookUrl = bookUrl)

    fun setAsUnreadSelected() {
        val list = state.selectedChaptersUrl.toList()
        appScope.launch(Dispatchers.Default) {
            appRepository.bookChapters.setAsUnread(list.map { it.first })
        }
    }

    fun setAsReadSelected() {
        val list = state.selectedChaptersUrl.toList()
        appScope.launch(Dispatchers.Default) {
            appRepository.bookChapters.setAsRead(list.map { it.first })
        }
    }

    fun setAsReadUpToSelected() {
        if (state.selectedChaptersUrl.size > 1) return
        val selectedIndex = state.selectedChaptersUrl.keys.firstOrNull()?.let { selectedUrl ->
            state.chapters.indexOfFirst { it.chapter.url == selectedUrl }
        } ?: return

        if (selectedIndex != -1) {
            val chaptersToMarkAsRead = state.chapters.take(selectedIndex + 1).map { it.chapter.url }
            appScope.launch(Dispatchers.Default) {
                appRepository.bookChapters.setAsRead(chaptersToMarkAsRead)
            }
        }
    }

    fun setAsReadUpToUnSelected() {
        if (state.selectedChaptersUrl.size > 1) return
        val selectedIndex = state.selectedChaptersUrl.keys.firstOrNull()?.let { selectedUrl ->
            state.chapters.indexOfFirst { it.chapter.url == selectedUrl }
        } ?: return

        if (selectedIndex != -1) {
            val chaptersToMarkAsUnread = state.chapters.take(selectedIndex + 1).map { it.chapter.url }
            appScope.launch(Dispatchers.Default) {
                appRepository.bookChapters.setAsUnread(chaptersToMarkAsUnread)
            }
        }
    }

    fun downloadSelected() {
        if (state.isLocalSource.value) return
        val list = state.selectedChaptersUrl.toList()
        appScope.launch(Dispatchers.Default) {
            list.forEach { appRepository.chapterBody.fetchBody(it.first) }
        }
    }

    fun deleteDownloadsSelected() {
        if (state.isLocalSource.value) return
        val list = state.selectedChaptersUrl.toList()
        appScope.launch(Dispatchers.Default) {
            appRepository.chapterBody.removeRows(list.map { it.first })
        }
    }

    fun onSelectionModeChapterClick(chapter: ChapterWithContext) {
        val url = chapter.chapter.url
        if (state.selectedChaptersUrl.containsKey(url)) {
            state.selectedChaptersUrl.remove(url)
        } else {
            state.selectedChaptersUrl[url] = Unit
        }
        lastSelectedChapterUrl = url
    }

    fun saveImageAsCover(uri: Uri) {
        appRepository.libraryBooks.saveImageAsCover(imageUri = uri, bookUrl = bookUrl)
    }

    fun onSelectionModeChapterLongClick(chapter: ChapterWithContext) {
        val url = chapter.chapter.url
        if (url != lastSelectedChapterUrl) {
            val indexOld = state.chapters.indexOfFirst { it.chapter.url == lastSelectedChapterUrl }
            val indexNew = state.chapters.indexOfFirst { it.chapter.url == url }
            val min = minOf(indexOld, indexNew)
            val max = maxOf(indexOld, indexNew)
            if (min >= 0 && max >= 0) {
                for (index in min..max) {
                    state.selectedChaptersUrl[state.chapters[index].chapter.url] = Unit
                }
                lastSelectedChapterUrl = state.chapters[indexNew].chapter.url
                return
            }
        }

        if (state.selectedChaptersUrl.containsKey(url)) {
            state.selectedChaptersUrl.remove(url)
        } else {
            state.selectedChaptersUrl[url] = Unit
        }
        lastSelectedChapterUrl = url
    }

    fun onChapterLongClick(chapter: ChapterWithContext) {
        val url = chapter.chapter.url
        state.selectedChaptersUrl[url] = Unit
        lastSelectedChapterUrl = url
    }

    fun onChapterDownload(chapter: ChapterWithContext) {
        if (state.isLocalSource.value) return
        appScope.launch {
            appRepository.chapterBody.fetchBody(chapter.chapter.url)
        }
    }

    fun unselectAll() {
        state.selectedChaptersUrl.clear()
    }

    fun selectAll() {
        state.chapters
            .toList()
            .map { it.chapter.url to Unit }
            .let { state.selectedChaptersUrl.putAll(it) }
    }

    fun invertSelection() {
        val allChaptersUrl = state.chapters.asSequence().map { it.chapter.url }.toSet()
        val selectedUrl = state.selectedChaptersUrl.asSequence().map { it.key }.toSet()
        val inverse = (allChaptersUrl - selectedUrl).asSequence().associateWith { }
        state.selectedChaptersUrl.clear()
        state.selectedChaptersUrl.putAll(inverse)
    }

    fun exportAsEpub() {
        if (state.isLocalSource.value) return
        workersInteractions.exportBookToEpub(
            bookUrl = bookUrl,
            bookTitle = bookTitle,
        )
        toasty.show(R.string.epub_export_started)
    }

    fun deleteBookFromDb() {
        appScope.launch(Dispatchers.IO) {
            val chapters = appRepository.bookChapters.chapters(bookUrl)
            appRepository.chapterBody.removeRows(chapters.map { it.url })
            appRepository.bookChapters.removeAllFromBook(bookUrl)
            appRepository.libraryBooks.remove(bookUrl)
            toasty.show(R.string.book_deleted)
            _bookDeletedEvent.emit(Unit)
        }
    }
}

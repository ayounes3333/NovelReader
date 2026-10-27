package my.noveldoksuha.interactor

import my.noveldokusha.core.domain.LibraryCategory

interface WorkersInteractions {
    fun checkForLibraryUpdates(libraryCategory: LibraryCategory)

    /**
     * Enqueues a one-time WorkManager job that downloads all chapters of the
     * given book and writes them as a single EPUB file to the Downloads directory.
     *
     * @param bookUrl   URL of the book page (must match a registered scraper source).
     * @param bookTitle Human-readable title used for the EPUB filename and metadata.
     * @return          The [androidx.work.Operation] representing the enqueued work.
     */
    fun exportBookToEpub(bookUrl: String, bookTitle: String)

    /**
     * Enqueues a one-time WorkManager job that runs an AI task over a set of
     * chapters. The job input must have been persisted beforehand via
     * [my.noveldoksuha.data.AiBulkJobStore.createJob]; the worker loads it by id.
     */
    fun runAiBulkTask(jobId: String)
}
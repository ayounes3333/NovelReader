package my.noveldokusha.tooling.quick_setup.transfer.resilience

import kotlinx.serialization.Serializable
import my.noveldokusha.tooling.quick_setup.transfer.TransferManifest

/**
 * Summary of a completed or interrupted transfer.
 * Shown to the user as a receipt of what was transferred.
 */
@Serializable
data class TransferSummary(
    val sessionId: String,
    val manifest: TransferManifest,
    val booksTransferred: Int,
    val chaptersTransferred: Int,
    val imagesTransferred: Int,
    val preferencesTransferred: Boolean,
    val durationMs: Long,
    val bytesTransferred: Long,
    val completedAt: Long,
    val wasResumed: Boolean,
    val errors: List<String> = emptyList(),
) {
    val totalItemsTransferred: Int
        get() = booksTransferred + chaptersTransferred + imagesTransferred + if (preferencesTransferred) 1 else 0

    val durationSeconds: Long
        get() = durationMs / 1000

    val isFullyComplete: Boolean
        get() = booksTransferred >= manifest.booksCount &&
                chaptersTransferred >= manifest.chaptersCount &&
                imagesTransferred >= manifest.imagesCount &&
                (preferencesTransferred || !manifest.hasPreferences)

    fun toDisplayString(): String {
        val sb = StringBuilder()
        sb.appendLine("Transfer Summary")
        sb.appendLine("━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Books: $booksTransferred / ${manifest.booksCount}")
        sb.appendLine("Chapters: $chaptersTransferred / ${manifest.chaptersCount}")
        sb.appendLine("Images: $imagesTransferred / ${manifest.imagesCount}")
        sb.appendLine("Settings: ${if (preferencesTransferred) "Yes" else "No"}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Duration: ${durationSeconds}s")
        if (wasResumed) {
            sb.appendLine("(Resumed from previous session)")
        }
        if (errors.isNotEmpty()) {
            sb.appendLine("Errors: ${errors.size}")
        }
        return sb.toString()
    }
}

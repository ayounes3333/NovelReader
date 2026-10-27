package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Non-destructive AI-generated revision of a chapter. The scraped/downloaded
 * original (`ChapterBody`) is never mutated; readers resolve the body through
 * the [active] revision when present.
 *
 * The pair (taskType, meta) identifies the kind of revision:
 * TITLE, SUMMARY, TRANSLATION (meta = target language), NARRATION, GRAMMAR
 * and CUSTOM (meta = prompt id).
 */
@Entity(
    indices = [Index("chapterUrl")]
)
data class ChapterAiRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterUrl: String,
    val taskType: String,
    val meta: String,
    val content: String,
    val model: String,
    val providerId: String,
    val active: Boolean,
    val createdEpochTimeMilli: Long = System.currentTimeMillis(),
)

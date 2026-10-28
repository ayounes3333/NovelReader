package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted AI assistant chat history, one conversation per book.
 *
 * [role] is the wire role (`user`, `assistant`, `tool`). Assistant messages
 * that requested tools store them in [toolCallsJson]; tool results store the
 * [toolCallId] they answer and the [toolName] that produced them.
 */
@Entity(
    indices = [Index("bookUrl")]
)
data class AiChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUrl: String,
    val role: String,
    val content: String,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val createdEpochTimeMilli: Long = System.currentTimeMillis(),
)

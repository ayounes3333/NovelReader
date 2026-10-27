package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-editable prompt template. Built-in rows are seeded on first use and can
 * be reset; user rows can be created for custom tasks or to override built-ins.
 *
 * When [taskType]+[meta] match a revision task, the prompt is used as the
 * system prompt for that task (e.g. taskType = TRANSLATION, meta = "es").
 */
@Entity
data class AiPrompt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val systemPrompt: String,
    val taskType: String,
    val meta: String = "",
    val isBuiltIn: Boolean = false,
)

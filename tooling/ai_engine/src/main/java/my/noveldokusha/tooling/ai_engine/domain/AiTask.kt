package my.noveldokusha.tooling.ai_engine.domain

/**
 * AI task that can be executed over a chapter body (and title).
 *
 * [key] is the stable identifier persisted on `ChapterAiRevision.taskType`.
 * [metaKey] carries the task parameter (target language, prompt id, ...).
 * The pair (taskType, meta) uniquely identifies the kind of generated revision.
 */
sealed interface AiTask {
    val key: String
    val metaKey: String

    data object CreateTitle : AiTask {
        override val key = "TITLE"
        override val metaKey = ""
    }

    data object CreateSummary : AiTask {
        override val key = "SUMMARY"
        override val metaKey = ""
    }

    data class Translate(val targetLanguage: String) : AiTask {
        override val key = "TRANSLATION"
        override val metaKey = targetLanguage
    }

    data object FixNarration : AiTask {
        override val key = "NARRATION"
        override val metaKey = ""
    }

    data object FixGrammar : AiTask {
        override val key = "GRAMMAR"
        override val metaKey = ""
    }

    data class Custom(val promptId: Long) : AiTask {
        override val key = "CUSTOM"
        override val metaKey = promptId.toString()
    }
}

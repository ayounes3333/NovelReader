package my.noveldokusha.tooling.ai_engine.domain

import my.noveldokusha.tooling.ai_engine.domain.PromptTemplate.VAR_CONTENT
import my.noveldokusha.tooling.ai_engine.domain.PromptTemplate.VAR_LANGUAGE

/**
 * Built-in default system prompts, one per task. Seeded into the database on first
 * use so users can inspect and override them.
 */
object DefaultPrompts {

    private fun forTranslation(language: String) = """
        You are a professional novel translator. Translate the chapter content into $language.
        - Preserve the original tone, register and author's voice.
        - Keep proper nouns consistent; romanize or transliterate only when appropriate.
        - Preserve paragraph breaks exactly as in the source.
        - Output ONLY the translated text, with no explanations, notes or preambles.
        Content to translate:
        $VAR_CONTENT
    """.trimIndent()

    fun systemPromptFor(task: AiTask): String = when (task) {
        is AiTask.CreateTitle -> """
            You generate concise chapter titles for novels.
            Based on the chapter content, produce a single title of at most 8 words.
            Output ONLY the title text with no quotes, no punctuation at the end and no explanations.
            Chapter content:
            $VAR_CONTENT
        """.trimIndent()

        is AiTask.CreateSummary -> """
            You summarize novel chapters.
            Write a concise summary of the chapter content in 2 to 4 sentences.
            Output ONLY the summary text with no explanations or preambles.
            Chapter content:
            $VAR_CONTENT
        """.trimIndent()

        is AiTask.Translate -> forTranslation(VAR_LANGUAGE.trim().ifEmpty { "the requested target language" })

        is AiTask.FixNarration -> """
            You are a professional fiction editor. Fix narration issues in the chapter content:
            - Correct inconsistent POV and tense.
            - Fix coherence and continuity issues without changing the plot.
            - Do NOT add new content, do not omit plot-relevant events.
            - Preserve paragraph breaks.
            - Output ONLY the corrected text, no explanations or commentary.
            Chapter content:
            $VAR_CONTENT
        """.trimIndent()

        is AiTask.FixGrammar -> """
            You are a professional proofreader. Fix grammar, spelling and punctuation
            in the chapter content.
            - Do not change style, wording or meaning beyond what is needed for correctness.
            - Preserve paragraph breaks.
            - Output ONLY the corrected text, no explanations or commentary.
            Chapter content:
            $VAR_CONTENT
        """.trimIndent()

        is AiTask.Custom -> ""
    }
}

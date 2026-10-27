package my.noveldokusha.tooling.ai_engine.engine

/**
 * Splits chapter bodies at paragraph boundaries so each chunk fits a conservative
 * token budget. Character-based estimate is intentionally conservative: CJK text
 * averages ~0.5–1 tokens per char, latin text ~4 chars per token. The default
 * budget of 12_000 chars is a safe middle ground for ~3k tokens.
 */
object AiChunker {

    private const val DEFAULT_MAX_CHARS = 12_000

    /**
     * Splits [text] into chunks no longer than [maxChars], cutting only at
     * paragraph boundaries (double newline) or, as a fallback, at line boundaries.
     * Sequential chunk ordering is preserved and no text is dropped or duplicated.
     */
    fun split(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> {
        if (text.length <= maxChars) return listOf(text)

        val paragraphs = text.split(Regex("\n\\s*\n"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (paragraph in paragraphs) {
            // Single paragraph larger than the budget: split it by lines.
            if (paragraph.length > maxChars) {
                if (current.isNotBlank()) {
                    chunks += current.toString().trimEndParagraph()
                    current.clear()
                }
                chunks += splitByLines(paragraph, maxChars)
                continue
            }

            val extraSeparation = if (current.isNotEmpty()) "\n\n" else ""
            if (current.length + extraSeparation.length + paragraph.length > maxChars) {
                chunks += current.toString().trimEndParagraph()
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
        if (current.isNotBlank()) chunks += current.toString().trimEndParagraph()

        return chunks
    }

    /**
     * Head+tail sampling used for whole-document tasks (title, summary) on very
     * long chapters. Keeps the first [headChars] and last [tailChars] characters.
     */
    fun sampleHeadAndTail(
        text: String,
        headChars: Int = DEFAULT_MAX_CHARS / 2,
        tailChars: Int = DEFAULT_MAX_CHARS / 4,
    ): String {
        if (text.length <= headChars + tailChars) return text
        val head = text.take(headChars)
        val tail = text.takeLast(tailChars)
        return "$head\n\n[...]\n\n$tail"
    }

    private fun String.trimEndParagraph(): String = trimEnd('\n', '\r', ' ')

    private fun splitByLines(text: String, maxChars: Int): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (line in text.lineSequence()) {
            val extra = if (current.isEmpty()) 0 else 1
            if (current.length + extra + line.length > maxChars && current.isNotEmpty()) {
                chunks += current.toString().trimEndParagraph()
                current.clear()
            }
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        }
        if (current.isNotBlank()) chunks += current.toString().trimEndParagraph()
        return chunks
    }
}

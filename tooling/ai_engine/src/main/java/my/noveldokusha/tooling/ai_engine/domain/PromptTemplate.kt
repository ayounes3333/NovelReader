package my.noveldokusha.tooling.ai_engine.domain

/**
 * Prompt template with `{{variable}}` substitution.
 */
object PromptTemplate {

    const val VAR_CONTENT = "{{content}}"
    const val VAR_TITLE = "{{title}}"
    const val VAR_LANGUAGE = "{{language}}"

    val variables = listOf(VAR_CONTENT, VAR_TITLE, VAR_LANGUAGE)

    /**
     * Substitutes [variables] into [template]. Unknown variables are left untouched.
     * Missing variables resolve to empty string.
     */
    fun render(template: String, variables: Map<String, String> = emptyMap()): String =
        PromptTemplate.variables.fold(template) { acc, variable ->
            acc.replace(variable, variables[variable] ?: "")
        }

    /**
     * A valid content-editing template must reference the chapter content.
     */
    fun isValid(template: String): Boolean = template.contains(VAR_CONTENT)
}

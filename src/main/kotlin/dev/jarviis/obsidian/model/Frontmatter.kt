package dev.jarviis.obsidian.model

import java.time.LocalDate

/**
 * Parsed YAML frontmatter from an Obsidian note.
 * All fields are optional; missing frontmatter yields [Frontmatter.EMPTY].
 */
data class Frontmatter(
    val tags: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val date: LocalDate? = null,
    val title: String? = null,
    /** All other key-value pairs from the frontmatter block */
    val extra: Map<String, Any> = emptyMap(),
) {
    companion object {
        val EMPTY = Frontmatter()
    }
}

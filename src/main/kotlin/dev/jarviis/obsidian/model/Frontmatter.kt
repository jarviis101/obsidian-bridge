package dev.jarviis.obsidian.model

/**
 * Parsed YAML frontmatter from an Obsidian note.
 * All fields are optional; missing frontmatter yields [Frontmatter.EMPTY].
 */
data class Frontmatter(
    val aliases: List<String> = emptyList(),
) {
    companion object {
        val EMPTY = Frontmatter()
    }
}

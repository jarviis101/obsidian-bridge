package dev.jarviis.obsidian.model

/**
 * Represents a parsed Obsidian wiki-link in all its syntactic variants:
 *   [[Note]]
 *   [[Note|Alias]]
 *   [[Note#Heading]]
 *   [[Note#^block-id]]
 *   ![[Note]]  (embed / transclusion)
 */
data class WikiLink(
    val target: String,
    val isEmbed: Boolean = false,
    val startOffset: Int = 0,
)

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
    /** Raw target text before `|` and `#`, e.g. "Note Name" */
    val target: String,
    /** True when prefixed with `!` — embed/transclusion */
    val isEmbed: Boolean = false,
    /** Character offset of the opening `[[` (or `![[`) within the containing text */
    val startOffset: Int = 0,
)

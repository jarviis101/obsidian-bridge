package dev.jarviis.obsidian.model

/**
 * Represents a parsed Obsidian wiki-link in all its syntactic variants:
 *   [[Note]]
 *   [[Note|Alias]]
 *   [[Note#Heading]]
 *   [[Note#Heading|Alias]]
 *   [[Note#^block-id]]
 *   ![[Note]]  (embed / transclusion)
 */
data class WikiLink(
    /** Raw target text before `|` and `#`, e.g. "Note Name" */
    val target: String,
    /** Optional heading anchor, e.g. "Introduction" from [[Note#Introduction]] */
    val heading: String? = null,
    /** Optional block reference id, e.g. "abc123" from [[Note#^abc123]] */
    val blockId: String? = null,
    /** Display alias, e.g. "click here" from [[Note|click here]] */
    val alias: String? = null,
    /** True when prefixed with `!` — embed/transclusion */
    val isEmbed: Boolean = false,
    /** Character offset of the opening `[[` (or `![[`) within the containing text */
    val startOffset: Int = 0,
    /** Character offset just after the closing `]]` */
    val endOffset: Int = 0,
) {
    /** The full text of the inner link expression, e.g. "Note Name#Heading|Alias" */
    val innerText: String get() = buildString {
        append(target)
        if (blockId != null) append("#^").append(blockId)
        else if (heading != null) append("#").append(heading)
        if (alias != null) append("|").append(alias)
    }

    /** Display label shown in the editor (alias if present, otherwise target) */
    val displayText: String get() = alias ?: target
}

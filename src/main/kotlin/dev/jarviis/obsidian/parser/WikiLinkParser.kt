package dev.jarviis.obsidian.parser

import dev.jarviis.obsidian.model.WikiLink

/**
 * Stateless, regex-based scanner for Obsidian wiki-links.
 * Deliberately NOT a full Markdown AST parser — fast enough for 10k+ note vaults.
 *
 * Handles:
 *   [[Note]]
 *   [[Note|Alias]]
 *   [[Note#Heading]]
 *   [[Note#Heading|Alias]]
 *   [[Note#^block-id]]
 *   ![[Note]]  (embed)
 */
object WikiLinkParser {

    // Captures: group(1)=embed-prefix, group(2)=inner content
    private val WIKILINK_REGEX = Regex("""(!?)\[\[([^\[\]]+?)]]""")

    // Splits inner content into target, optional anchor, optional alias
    private val INNER_REGEX = Regex("""^([^#|]+?)(?:#(\^[^\]|]+|[^\]|]+))?(?:\|([^\]]+))?$""")

    /** Parse all wiki-links from [text], recording character offsets relative to [text]. */
    fun parse(text: String): List<WikiLink> {
        val results = mutableListOf<WikiLink>()
        for (match in WIKILINK_REGEX.findAll(text)) {
            val isEmbed = match.groupValues[1] == "!"
            // In Markdown tables `|` is escaped as `\|` — unescape before parsing
            val inner = match.groupValues[2].replace("\\|", "|")
            val innerMatch = INNER_REGEX.matchEntire(inner.trim()) ?: continue

            val target = innerMatch.groupValues[1].trim()
            val anchor = innerMatch.groupValues[2].trim().takeIf { it.isNotEmpty() }
            val alias = innerMatch.groupValues[3].trim().takeIf { it.isNotEmpty() }

            val (heading, blockId) = when {
                anchor == null -> null to null
                anchor.startsWith("^") -> null to anchor.removePrefix("^")
                else -> anchor to null
            }

            results += WikiLink(
                target = target,
                heading = heading,
                blockId = blockId,
                alias = alias,
                isEmbed = isEmbed,
                startOffset = match.range.first,
                endOffset = match.range.last + 1,
            )
        }
        return results
    }

    /**
     * Returns the [WikiLink] whose link span contains [offset], or null.
     * Used by PSI reference contributors to find the link at the caret.
     */
    fun findAt(text: String, offset: Int): WikiLink? =
        parse(text).firstOrNull { offset in it.startOffset until it.endOffset }
}

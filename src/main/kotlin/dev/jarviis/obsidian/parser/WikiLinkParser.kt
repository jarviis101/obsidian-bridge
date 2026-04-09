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

    // Splits inner content into target (and optional anchor/alias which we discard)
    private val TARGET_REGEX = Regex("""^([^#|]+?)(?:#[^\]|]+)?(?:\|[^\]]+)?$""")

    /** Parse all wiki-links from [text], recording character offsets relative to [text]. */
    fun parse(text: String): List<WikiLink> {
        val results = mutableListOf<WikiLink>()
        for (match in WIKILINK_REGEX.findAll(text)) {
            val isEmbed = match.groupValues[1] == "!"
            // In Markdown tables `|` is escaped as `\|` — unescape before parsing
            val inner = match.groupValues[2].replace("\\|", "|")
            val target = TARGET_REGEX.matchEntire(inner.trim())?.groupValues?.get(1)?.trim()
                ?: continue

            results += WikiLink(
                target = target,
                isEmbed = isEmbed,
                startOffset = match.range.first,
            )
        }
        return results
    }
}

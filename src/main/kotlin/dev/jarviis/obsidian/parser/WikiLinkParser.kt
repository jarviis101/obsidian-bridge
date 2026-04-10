package dev.jarviis.obsidian.parser

import dev.jarviis.obsidian.model.WikiLink

/** Stateless, regex-based scanner for Obsidian wiki-links. Not a full Markdown AST parser. */
object WikiLinkParser {
    private val WIKILINK_REGEX = Regex("""(!?)\[\[([^\[\]]+?)]]""")
    private val TARGET_REGEX = Regex("""^([^#|]+?)(?:#[^\]|]+)?(?:\|[^\]]+)?$""")

    fun parse(text: String): List<WikiLink> {
        val results = mutableListOf<WikiLink>()
        for (match in WIKILINK_REGEX.findAll(text)) {
            val isEmbed = match.groupValues[1] == "!"
            val inner = match.groupValues[2].replace("\\|", "|")
            val target = TARGET_REGEX.matchEntire(inner.trim())?.groupValues?.get(1)?.trim() ?: continue

            results += WikiLink(
                target = target,
                isEmbed = isEmbed,
                startOffset = match.range.first,
            )
        }
        return results
    }
}

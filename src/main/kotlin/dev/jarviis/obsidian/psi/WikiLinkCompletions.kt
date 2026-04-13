package dev.jarviis.obsidian.psi

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import dev.jarviis.obsidian.ObsidianIcons
import dev.jarviis.obsidian.model.ObsidianNote

/**
 * Result of locating an open wiki-link before the caret.
 * @param openBracket index of `[[` in the file text
 * @param prefix text between `[[` and the caret, stripped of heading and alias
 */
internal data class WikiLinkAtCaret(val openBracket: Int, val prefix: String)

/**
 * Finds an unclosed `[[` before [offset] in [text] and extracts the typed prefix.
 * Returns null when:
 * - the caret is too close to the start of the file
 * - no `[[` exists before [offset]
 * - the `[[` is already closed by `]]` before [offset]
 * - the caret has not moved past `[[` yet (no room for a prefix)
 */
internal fun findWikiLinkAtCaret(text: String, offset: Int): WikiLinkAtCaret? {
    val safeOffset = offset.coerceAtMost(text.length)
    if (safeOffset < 2) return null

    val openBracket = text.lastIndexOf("[[", safeOffset - 1)
    if (openBracket < 0) return null

    val closeBracket = text.indexOf("]]", openBracket)
    if (closeBracket in openBracket until safeOffset) return null

    val prefixStart = openBracket + 2
    if (prefixStart > safeOffset) return null

    val prefix = text.substring(prefixStart, safeOffset)
        .substringBefore('#')
        .substringBefore('|')

    return WikiLinkAtCaret(openBracket, prefix)
}

/**
 * Returns true when the `[[` at [openBracket] in [text] is preceded by a
 * comment marker on the same line: `//`, `#`, `*` or slash-star.
 * Used as a text-based fallback when the PSI element does not implement PsiComment.
 */
internal fun isInCommentByLinePrefix(text: String, openBracket: Int): Boolean {
    val lineStart = text.lastIndexOf('\n', openBracket).let { if (it < 0) 0 else it + 1 }
    val linePrefix = text.substring(lineStart, openBracket).trimStart()
    return linePrefix.startsWith("//") || linePrefix.startsWith("#") ||
        linePrefix.startsWith("*") || linePrefix.startsWith("/*")
}

/**
 * Fills [result] with wiki-link completions filtered by [prefix].
 * Shared between [WikiLinkCompletionContributor] (Markdown) and
 * [WikiLinkCommentCompletionContributor] (source-code comments).
 */
internal fun addNoteCompletions(allNotes: List<ObsidianNote>, prefix: String, result: CompletionResultSet) {
    val duplicateNames = allNotes.groupingBy { it.name.lowercase() }.eachCount()
        .filterValues { it > 1 }.keys

    val q = prefix.lowercase()
    val notes = if (prefix.isBlank()) allNotes
    else allNotes.filter { note ->
        val relPath = note.relativePath.toString().removeSuffix(".md").replace('\\', '/')
        note.name.lowercase().contains(q) || relPath.lowercase().contains(q)
    }

    for (note in notes) {
        val isDuplicate = note.name.lowercase() in duplicateNames
        val relPath = note.relativePath.toString().removeSuffix(".md").replace('\\', '/')
        val insertText = if (isDuplicate) relPath else note.name
        val tailText = buildString {
            if (isDuplicate) append(" $relPath")
            if (note.frontmatter.aliases.isNotEmpty())
                append(" (${note.frontmatter.aliases.joinToString()})")
        }
        result.addElement(
            LookupElementBuilder.create(insertText)
                .withPresentableText(note.name)
                .withIcon(ObsidianIcons.Note)
                .withTypeText(note.vaultName, true)
                .withTailText(tailText, true)
        )
    }
    result.stopHere()
}

package dev.jarviis.obsidian.psi

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import dev.jarviis.obsidian.ObsidianIcons
import dev.jarviis.obsidian.vault.VaultManager

/**
 * Provides note-name completions inside `[[` ... `]]` blocks in Markdown files.
 *
 * Triggered when the caret is inside a `[[` prefix that hasn't been closed yet,
 * or within an already-open wiki-link target.
 */
class WikiLinkCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return

        val text = parameters.originalFile.text ?: return
        val offset = parameters.offset

        val openBracket = text.lastIndexOf("[[", offset - 1)
        if (openBracket < 0) return
        val closeBracket = text.indexOf("]]", openBracket)
        if (closeBracket in openBracket until offset) return

        val prefix = text.substring(openBracket + 2, offset)
            .substringBefore('#')
            .substringBefore('|')

        val manager = service<VaultManager>()
        val project = parameters.position.project
        val index = manager.indexForProject(project) ?: return

        val allNotes = index.allNotes()
        val notes = if (prefix.isBlank()) allNotes
                    else { val q = prefix.lowercase(); allNotes.filter { it.name.lowercase().contains(q) } }

        for (note in notes) {
            val element = LookupElementBuilder.create(note.name)
                .withIcon(ObsidianIcons.Note)
                .withTypeText(note.vaultName, true)
                .withTailText(
                    if (note.frontmatter.aliases.isNotEmpty())
                        " (${note.frontmatter.aliases.joinToString()})"
                    else "",
                    true
                )
            result.addElement(element)
        }
        result.stopHere()
    }
}

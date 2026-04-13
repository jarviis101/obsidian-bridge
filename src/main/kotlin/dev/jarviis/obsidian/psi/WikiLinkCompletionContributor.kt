package dev.jarviis.obsidian.psi

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.components.service
import dev.jarviis.obsidian.vault.VaultManager

/**
 * Provides note-name completions inside `[[` ... `]]` blocks in Markdown files.
 */
class WikiLinkCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return

        val text = parameters.originalFile.text ?: return
        val (_, prefix) = findWikiLinkAtCaret(text, parameters.offset) ?: return

        val index = service<VaultManager>().indexForProject(parameters.position.project) ?: return
        addNoteCompletions(index.allNotes(), prefix, result)
    }
}

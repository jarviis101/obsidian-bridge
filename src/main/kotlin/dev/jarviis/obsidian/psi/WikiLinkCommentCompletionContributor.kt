package dev.jarviis.obsidian.psi

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import dev.jarviis.obsidian.vault.VaultManager

/**
 * Provides [[note]] completions inside comments in source-code files.
 *
 * Registered without a language restriction so the platform does not filter
 * by language. Exits early unless the caret is positioned after [[ inside a comment.
 */
class WikiLinkCommentCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val text = parameters.originalFile.text
                    val (openBracket, prefix) = findWikiLinkAtCaret(text, parameters.offset) ?: return

                    val inCommentPsi = parameters.originalFile.findElementAt(openBracket)?.let { el ->
                        el is PsiComment
                            || el.parent is PsiComment
                            || PsiTreeUtil.getParentOfType(el, PsiComment::class.java, false) != null
                    } ?: false

                    if (!inCommentPsi && !isInCommentByLinePrefix(text, openBracket)) return

                    val index = service<VaultManager>().indexForProject(parameters.position.project) ?: return
                    addNoteCompletions(index.allNotes(), prefix, result)
                }
            }
        )
    }
}

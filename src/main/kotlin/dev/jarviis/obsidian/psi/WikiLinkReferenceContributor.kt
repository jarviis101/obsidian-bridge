package dev.jarviis.obsidian.psi

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import dev.jarviis.obsidian.parser.WikiLinkParser

/**
 * Injects [WikiLinkReference] into every wiki-link `[[target]]` found in Markdown files.
 * The reference [TextRange] covers only the target name (excluding `[[`, `]]`, alias, heading).
 */
class WikiLinkReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            WikiLinkReferenceProvider(),
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )
    }

    private class WikiLinkReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(
            element: PsiElement,
            context: ProcessingContext,
        ): Array<PsiReference> {
            val text = element.text ?: return PsiReference.EMPTY_ARRAY
            val links = WikiLinkParser.parse(text)
            if (links.isEmpty()) return PsiReference.EMPTY_ARRAY

            return links.map { link ->
                val prefixLen = if (link.isEmbed) 3 else 2
                val rangeStart = link.startOffset + prefixLen
                val rangeEnd = rangeStart + link.target.length
                WikiLinkReference(
                    element = element,
                    range = TextRange(rangeStart, rangeEnd),
                    target = link.target,
                )
            }.toTypedArray()
        }
    }
}

package dev.jarviis.obsidian.psi

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.util.IncorrectOperationException
import dev.jarviis.obsidian.vault.VaultManager

/**
 * PSI reference for a wiki-link target, e.g. the "Note Name" in [[Note Name]].
 * Resolution delegates to [VaultManager] which uses [dev.jarviis.obsidian.vault.VaultIndex].
 */
class WikiLinkReference(
    element: PsiElement,
    range: TextRange,
    private val target: String,
) : PsiReferenceBase<PsiElement>(element, range, true) {

    override fun resolve(): PsiElement? {
        val manager = service<VaultManager>()
        val contextPath = element.containingFile?.virtualFile?.path
            ?.let { java.nio.file.Paths.get(it) }
        val note = manager.resolveInProject(target, element.project, contextPath) ?: return null
        val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByNioFile(note.path) ?: return null
        return PsiManager.getInstance(element.project).findFile(vFile)
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        // Replace only the target part of the inner text, preserving alias and heading
        val doc = com.intellij.openapi.editor.EditorFactory.getInstance()
            .let { element.containingFile?.viewProvider?.document } ?: throw IncorrectOperationException()
        val nameWithoutExt = newElementName.removeSuffix(".md")
        // Delegate to default range replacement
        return super.handleElementRename(nameWithoutExt)
    }

    override fun getVariants(): Array<Any> = emptyArray() // handled by WikiLinkCompletionContributor
}

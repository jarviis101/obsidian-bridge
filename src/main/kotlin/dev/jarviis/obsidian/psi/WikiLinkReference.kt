package dev.jarviis.obsidian.psi

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import dev.jarviis.obsidian.vault.VaultManager

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
        val nameWithoutExt = newElementName.removeSuffix(".md")
        return super.handleElementRename(nameWithoutExt)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

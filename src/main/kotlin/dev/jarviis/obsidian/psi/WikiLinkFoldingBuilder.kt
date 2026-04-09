package dev.jarviis.obsidian.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.jarviis.obsidian.vault.VaultManager
import java.nio.file.Paths

class WikiLinkFoldingBuilder : FoldingBuilderEx(), DumbAware {

    private val WIKILINK_REGEX = Regex("""(!?)\[\[([^\[\]]+?)]]""")

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (quick) return emptyArray()

        val file = root.containingFile ?: return emptyArray()
        if (!file.name.endsWith(".md")) return emptyArray()

        val virtualFile = file.virtualFile ?: return emptyArray()
        val path = Paths.get(virtualFile.path)

        val manager = try { service<VaultManager>() } catch (_: Exception) { return emptyArray() }
        val index = manager.indexForPath(path) ?: return emptyArray()

        val editingRange = WikiLinkEditingState.editingRange(document)

        val descriptors = mutableListOf<FoldingDescriptor>()
        val text = document.charsSequence

        for (match in WIKILINK_REGEX.findAll(text)) {
            if (match.groupValues[1] == "!") continue
            if (editingRange != null &&
                match.range.first <= editingRange.last &&
                match.range.last >= editingRange.first) continue

            val inner = match.groupValues[2].replace("\\|", "|")
            val pipeIdx = inner.indexOf('|')
            val rawTarget = (if (pipeIdx >= 0) inner.substring(0, pipeIdx) else inner).trim()
            val alias = if (pipeIdx >= 0) inner.substring(pipeIdx + 1).trim() else null
            val target = rawTarget.substringBefore('#').trim()
            if (target.isBlank()) continue

            val note = index.resolve(target, path) ?: continue
            val displayText = alias?.takeIf { it.isNotBlank() } ?: note.name
            val range = TextRange(match.range.first, match.range.last + 1)
            descriptors.add(FoldingDescriptor(root.node, range, null, displayText))
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = "..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}

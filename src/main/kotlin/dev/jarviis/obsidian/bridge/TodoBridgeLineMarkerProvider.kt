package dev.jarviis.obsidian.bridge

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import dev.jarviis.obsidian.ObsidianBundle
import dev.jarviis.obsidian.ObsidianIcons
import dev.jarviis.obsidian.psi.isInCommentByLinePrefix
import dev.jarviis.obsidian.vault.VaultManager
import java.awt.event.MouseEvent
import java.nio.file.Paths

private fun PsiElement.isOnCommentLine(): Boolean {
    val file = containingFile?.text ?: return false
    return isInCommentByLinePrefix(file, textRange.startOffset)
}

/**
 * Adds a gutter icon to source-code lines containing `TODO: [[Note]]` or `FIXME: [[Note]]`.
 * Clicking the icon opens the linked note in the IDE editor.
 */
class TodoBridgeLineMarkerProvider : LineMarkerProvider {

    private val BRIDGE_PATTERN = Regex("""(?:TODO|FIXME)[:\s]+\[\[([^\]|#]+)""", RegexOption.IGNORE_CASE)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null

        val inComment = element is PsiComment
            || element.parent is PsiComment
            || element.isOnCommentLine()
        if (!inComment) return null

        val match = BRIDGE_PATTERN.find(element.text) ?: return null
        val noteName = match.groupValues[1].trim()

        val manager = service<VaultManager>()
        val contextPath = element.containingFile?.virtualFile?.path?.let { Paths.get(it) }
        val note = manager.resolve(noteName, contextPath) ?: return null

        val tooltip = ObsidianBundle.message("bridge.tooltip", note.name)

        return LineMarkerInfo(
            element,
            element.textRange,
            ObsidianIcons.Bridge,
            { tooltip },
            { _: MouseEvent, psiElement: PsiElement ->
                val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .findFileByNioFile(note.path) ?: return@LineMarkerInfo
                FileEditorManager.getInstance(psiElement.project).openFile(vFile, true)
            },
            GutterIconRenderer.Alignment.LEFT,
            { tooltip },
        )
    }
}

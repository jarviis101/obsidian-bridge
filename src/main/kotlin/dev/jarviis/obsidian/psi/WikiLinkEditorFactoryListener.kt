package dev.jarviis.obsidian.psi

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import dev.jarviis.obsidian.vault.VaultManager
import java.awt.event.MouseEvent
import java.nio.file.Paths

private val WIKILINK_REGEX = Regex("""(!?)\[\[([^\[\]]+?)]]""")

class WikiLinkEditorFactoryListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        editor.addEditorMouseListener(WikiLinkClickListener())
        editor.caretModel.addCaretListener(WikiLinkCaretListener(editor))
        applyWikiLinkFoldStyle(editor)
    }

    private fun applyWikiLinkFoldStyle(editor: Editor) {
        val virtualFile = editor.virtualFile ?: return
        if (!virtualFile.name.endsWith(".md")) return
        val editorEx = editor as? EditorEx ?: return
        val globalScheme = EditorColorsManager.getInstance().globalScheme
        val linkColor = globalScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)?.foregroundColor
        editorEx.colorsScheme.setAttributes(
            EditorColors.FOLDED_TEXT_ATTRIBUTES,
            TextAttributes().apply {
                foregroundColor = linkColor
                effectType = EffectType.LINE_UNDERSCORE
                effectColor = linkColor
            }
        )
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        WikiLinkEditingState.clearEditing(event.editor.document)
    }
}

// ── Mouse listener ────────────────────────────────────────────────────────────

private class WikiLinkClickListener : EditorMouseListener {

    override fun mousePressed(event: EditorMouseEvent) {
        if (event.mouseEvent.button != MouseEvent.BUTTON1) return
        val editor = event.editor
        val project = editor.project ?: return
        val virtualFile = editor.virtualFile ?: return
        if (!virtualFile.name.endsWith(".md")) return

        val offset = editor.logicalPositionToOffset(event.logicalPosition)
        val region = editor.foldingModel.getCollapsedRegionAtOffset(offset) ?: return

        if (event.mouseEvent.isMetaDown) {
            // Cmd+Click → expand for editing
            editor.foldingModel.runBatchFoldingOperation {
                region.isExpanded = true
            }
            editor.caretModel.moveToOffset(offset)
            WikiLinkEditingState.setEditing(
                editor.document,
                region.startOffset until region.endOffset
            )
        } else {
            // Regular click → navigate
            val regionText = editor.document.charsSequence
                .subSequence(region.startOffset, region.endOffset).toString()
            val target = extractTarget(regionText) ?: return
            val path = Paths.get(virtualFile.path)
            openNote(project, target, path)
        }
        event.mouseEvent.consume()
    }
}

// ── Caret listener ────────────────────────────────────────────────────────────

private class WikiLinkCaretListener(private val editor: Editor) : CaretListener {

    override fun caretPositionChanged(event: CaretEvent) {
        val virtualFile = editor.virtualFile ?: return
        if (!virtualFile.name.endsWith(".md")) return

        val text = editor.document.charsSequence
        val oldOffset = editor.logicalPositionToOffset(event.oldPosition)
        val newOffset = event.caret?.offset ?: return

        val oldRange = findWikiLinkRangeAt(text, oldOffset)
        val newRange = findWikiLinkRangeAt(text, newOffset)

        // Keep editing state current so FoldingBuilder skips this range
        if (newRange != null) {
            WikiLinkEditingState.setEditing(editor.document, newRange)
        } else {
            WikiLinkEditingState.clearEditing(editor.document)
        }

        // Caret left a wiki-link region → auto-fold it
        if (oldRange != null && oldRange != newRange) {
            foldRange(oldRange)
        }
    }

    private fun foldRange(range: IntRange) {
        editor.foldingModel.runBatchFoldingOperation {
            // Collapse existing expanded fold
            val existing = editor.foldingModel.allFoldRegions
                .find { it.startOffset == range.first && it.endOffset == range.last + 1 }
            if (existing != null) {
                if (existing.isExpanded) existing.isExpanded = false
                return@runBatchFoldingOperation
            }

            // No fold yet (user just created a new link) — create one if note exists
            val virtualFile = editor.virtualFile ?: return@runBatchFoldingOperation
            val path = Paths.get(virtualFile.path)
            val wikiText = editor.document.charsSequence
                .subSequence(range.first, range.last + 1).toString()
            val target = extractTarget(wikiText) ?: return@runBatchFoldingOperation
            val manager = try { service<VaultManager>() } catch (_: Exception) { return@runBatchFoldingOperation }
            val index = manager.indexForPath(path) ?: return@runBatchFoldingOperation
            val note = index.resolve(target, path) ?: return@runBatchFoldingOperation
            val displayText = extractAlias(wikiText)?.takeIf { it.isNotBlank() } ?: note.name
            val newFold = editor.foldingModel.addFoldRegion(range.first, range.last + 1, displayText)
            newFold?.isExpanded = false
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun findWikiLinkRangeAt(text: CharSequence, offset: Int): IntRange? {
    for (match in WIKILINK_REGEX.findAll(text)) {
        if (match.range.first > offset) break
        if (offset in match.range) return match.range
    }
    return null
}

private fun extractTarget(text: String): String? {
    val match = WIKILINK_REGEX.find(text) ?: return null
    if (match.groupValues[1] == "!") return null
    val inner = match.groupValues[2].replace("\\|", "|")
    val pipeIdx = inner.indexOf('|')
    val rawTarget = (if (pipeIdx >= 0) inner.substring(0, pipeIdx) else inner).trim()
    return rawTarget.substringBefore('#').trim().takeIf { it.isNotBlank() }
}

private fun extractAlias(text: String): String? {
    val match = WIKILINK_REGEX.find(text) ?: return null
    val inner = match.groupValues[2].replace("\\|", "|")
    val pipeIdx = inner.indexOf('|')
    return if (pipeIdx >= 0) inner.substring(pipeIdx + 1).trim() else null
}

private fun openNote(
    project: com.intellij.openapi.project.Project,
    target: String,
    contextPath: java.nio.file.Path
): Boolean {
    val manager = try { service<VaultManager>() } catch (_: Exception) { return false }
    val index = manager.indexForPath(contextPath) ?: return false
    val note = index.resolve(target, contextPath) ?: return false
    val vFile = LocalFileSystem.getInstance().findFileByNioFile(note.path) ?: return false
    FileEditorManager.getInstance(project).openFile(vFile, true)
    return true
}

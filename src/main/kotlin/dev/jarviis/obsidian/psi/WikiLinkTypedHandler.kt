package dev.jarviis.obsidian.psi

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Triggers wiki-link completion popup automatically when the user types [[ in any file.
 * Handles both Markdown and source-code comments without requiring Ctrl+Space.
 */
class WikiLinkTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != '[') return Result.CONTINUE

        val offset = editor.caretModel.offset
        if (offset < 1) return Result.CONTINUE

        val prevChar = editor.document.charsSequence.let {
            if (offset - 1 < it.length) it[offset - 1] else return Result.CONTINUE
        }
        if (prevChar != '[') return Result.CONTINUE

        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)

        return Result.CONTINUE
    }
}

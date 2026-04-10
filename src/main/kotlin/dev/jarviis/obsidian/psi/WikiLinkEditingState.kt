package dev.jarviis.obsidian.psi

import com.intellij.openapi.editor.Document
import java.util.concurrent.ConcurrentHashMap

object WikiLinkEditingState {
    private val state = ConcurrentHashMap<Document, IntRange>()

    fun setEditing(document: Document, range: IntRange) { state[document] = range }
    fun clearEditing(document: Document) { state.remove(document) }
    fun editingRange(document: Document): IntRange? = state[document]
}

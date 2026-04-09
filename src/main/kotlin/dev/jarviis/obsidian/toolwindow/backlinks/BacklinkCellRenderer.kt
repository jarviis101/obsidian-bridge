package dev.jarviis.obsidian.toolwindow.backlinks

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import dev.jarviis.obsidian.ObsidianIcons
import dev.jarviis.obsidian.model.ObsidianNote
import javax.swing.JList

class BacklinkCellRenderer : ColoredListCellRenderer<ObsidianNote>() {
    override fun customizeCellRenderer(
        list: JList<out ObsidianNote>,
        value: ObsidianNote,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        icon = ObsidianIcons.Note
        append(value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        append("  ${value.relativePath.parent ?: ""}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
}

package dev.jarviis.obsidian.settings

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import dev.jarviis.obsidian.ObsidianIcons
import dev.jarviis.obsidian.model.VaultDescriptor
import javax.swing.JList

class VaultListCellRenderer : ColoredListCellRenderer<VaultDescriptor>() {
    override fun customizeCellRenderer(
        list: JList<out VaultDescriptor>,
        value: VaultDescriptor,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        icon = ObsidianIcons.Note
        append(value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        append("  ${value.rootPathString}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
}

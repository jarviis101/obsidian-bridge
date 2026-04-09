package dev.jarviis.obsidian.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import dev.jarviis.obsidian.ObsidianBundle
import dev.jarviis.obsidian.model.VaultDescriptor
import dev.jarviis.obsidian.vault.VaultManager
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class AppSettingsConfigurable : Configurable {

    private var panel: DialogPanel? = null
    private val listModel = CollectionListModel<VaultDescriptor>()
    private val vaultList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = VaultListCellRenderer()
    }
    private val crossVaultCheck = JBCheckBox(ObsidianBundle.message("settings.cross.vault.search"))

    override fun getDisplayName(): String = ObsidianBundle.message("settings.app.display.name")

    override fun createComponent(): JComponent {
        val settings = AppVaultSettings.getInstance()
        listModel.replaceAll(settings.vaults)
        crossVaultCheck.isSelected = settings.crossVaultSearch

        val decorator = ToolbarDecorator.createDecorator(vaultList)
            .setAddAction { addVault() }
            .setRemoveAction { removeSelectedVault() }
            .disableUpDownActions()
            .createPanel()

        return panel {
            group(ObsidianBundle.message("settings.vaults.label")) {
                row { cell(decorator).align(AlignX.FILL).resizableColumn() }
            }
            row { cell(crossVaultCheck) }
        }.also { panel = it }
    }

    override fun isModified(): Boolean {
        val settings = AppVaultSettings.getInstance()
        return listModel.items != settings.vaults || crossVaultCheck.isSelected != settings.crossVaultSearch
    }

    override fun apply() {
        val settings = AppVaultSettings.getInstance()
        settings.vaults = listModel.items
        settings.crossVaultSearch = crossVaultCheck.isSelected
        // Sync VaultManager with the updated vault list
        service<VaultManager>().replaceVaults(settings.vaults)
    }

    override fun reset() {
        val settings = AppVaultSettings.getInstance()
        listModel.replaceAll(settings.vaults)
        crossVaultCheck.isSelected = settings.crossVaultSearch
    }

    private fun addVault() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = ObsidianBundle.message("settings.vault.path.label")
        }
        val chooser = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, null, null) ?: return
        val path = chooser.path
        val vaultDescriptor = VaultDescriptor(name = chooser.name, rootPathString = path)
        if (!vaultDescriptor.isValid()) {
            Messages.showWarningDialog(
                ObsidianBundle.message("settings.vault.path.invalid"),
                ObsidianBundle.message("settings.app.display.name")
            )
            return
        }
        if (!vaultDescriptor.hasObsidianConfig) {
            val proceed = Messages.showYesNoDialog(
                "The selected folder has no .obsidian/ directory. It will work as a plain Markdown vault (no daily notes config, no template config). Continue?",
                ObsidianBundle.message("settings.app.display.name"),
                Messages.getQuestionIcon()
            )
            if (proceed != Messages.YES) return
        }
        listModel.add(vaultDescriptor)
    }

    private fun removeSelectedVault() {
        val selected = vaultList.selectedValue ?: return
        listModel.remove(selected)
    }
}

package dev.jarviis.obsidian.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import dev.jarviis.obsidian.ObsidianBundle
import dev.jarviis.obsidian.model.VaultDescriptor
import dev.jarviis.obsidian.vault.VaultManager
import dev.jarviis.obsidian.vault.detectVaultIn
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class ProjectSettingsConfigurable(private val project: Project) : Configurable {

    private val listModel = CollectionListModel<VaultDescriptor>()
    private val vaultList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = VaultListCellRenderer()
    }

    override fun getDisplayName(): String = ObsidianBundle.message("settings.project.display.name")

    override fun createComponent(): JComponent {
        listModel.replaceAll(ProjectVaultSettings.getInstance(project).vaults)

        val decorator = ToolbarDecorator.createDecorator(vaultList)
            .setAddAction { addVault() }
            .setAddActionUpdater { listModel.isEmpty }
            .setRemoveAction { removeSelectedVault() }
            .setRemoveActionUpdater { listModel.size > 0 }
            .disableUpDownActions()
            .createPanel()

        return panel {
            group(ObsidianBundle.message("settings.vaults.label")) {
                row { cell(decorator).align(AlignX.FILL).resizableColumn() }
                row {
                    button(ObsidianBundle.message("settings.project.vault.scan")) { scanForVault() }
                }
            }
        }
    }

    override fun isModified(): Boolean =
        listModel.items != ProjectVaultSettings.getInstance(project).vaults

    override fun apply() {
        ProjectVaultSettings.getInstance(project).vaults = listModel.items
        val manager = service<VaultManager>()
        for (descriptor in listModel.items) {
            if (descriptor.isValid()) manager.registerVault(descriptor)
        }
    }

    override fun reset() {
        listModel.replaceAll(ProjectVaultSettings.getInstance(project).vaults)
    }

    private fun addVault() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = ObsidianBundle.message("settings.vault.path.label")
        }
        val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return
        val vaultDescriptor = VaultDescriptor(name = chosen.name, rootPathString = chosen.path)

        if (!vaultDescriptor.isValid()) {
            Messages.showWarningDialog(
                project,
                ObsidianBundle.message("settings.vault.path.invalid"),
                ObsidianBundle.message("settings.project.display.name")
            )
            return
        }
        if (!vaultDescriptor.hasObsidianConfig) {
            val proceed = Messages.showYesNoDialog(
                project,
                "The selected folder has no .obsidian/ directory. It will work as a plain Markdown vault. Continue?",
                ObsidianBundle.message("settings.project.display.name"),
                Messages.getQuestionIcon()
            )
            if (proceed != Messages.YES) return
        }
        if (listModel.items.none { it.rootPathString == vaultDescriptor.rootPathString }) {
            listModel.add(vaultDescriptor)
        }
    }

    private fun removeSelectedVault() {
        val selected = vaultList.selectedValue ?: return
        listModel.remove(selected)
    }

    private fun scanForVault() {
        val basePath = project.basePath ?: return
        val detected = detectVaultIn(Paths.get(basePath))

        if (detected == null) {
            Messages.showInfoMessage(
                project,
                ObsidianBundle.message("settings.project.vault.scan.not.found"),
                ObsidianBundle.message("settings.project.display.name")
            )
            return
        }

        listModel.replaceAll(listOf(detected))

        Messages.showInfoMessage(
            project,
            ObsidianBundle.message("settings.project.vault.scan.found", detected.name),
            ObsidianBundle.message("settings.project.display.name")
        )
    }
}

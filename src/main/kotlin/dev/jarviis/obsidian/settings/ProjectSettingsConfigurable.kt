package dev.jarviis.obsidian.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import dev.jarviis.obsidian.ObsidianBundle
import dev.jarviis.obsidian.vault.VaultManager
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class ProjectSettingsConfigurable(private val project: Project) : Configurable {

    private val comboModel = DefaultComboBoxModel<String>()

    override fun getDisplayName(): String = ObsidianBundle.message("settings.project.display.name")

    override fun createComponent(): JComponent {
        comboModel.removeAllElements()
        val allVaults = service<VaultManager>().registeredVaults()

        return if (allVaults.isEmpty()) {
            panel { row { cell(JBLabel(ObsidianBundle.message("vault.not.configured"))) } }
        } else {
            comboModel.addElement(ObsidianBundle.message("settings.project.vault.none"))
            allVaults.forEach { comboModel.addElement(it.name) }

            val current = ProjectVaultSettings.getInstance(project).activeVaultName
            comboModel.selectedItem = current ?: comboModel.getElementAt(0)

            panel {
                row(ObsidianBundle.message("settings.project.vault.label")) {
                    comboBox(comboModel)
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val saved = ProjectVaultSettings.getInstance(project).activeVaultName
        val selected = selectedVaultName()
        return saved != selected
    }

    override fun apply() {
        val name = selectedVaultName()
        val settings = ProjectVaultSettings.getInstance(project)
        settings.activeVaultName = name
        settings.activeVaultPath = name?.let { n ->
            service<VaultManager>().registeredVaults().firstOrNull { it.name == n }?.rootPathString
        }
    }

    override fun reset() {
        val current = ProjectVaultSettings.getInstance(project).activeVaultName
        comboModel.selectedItem = current ?: comboModel.getElementAt(0)
    }

    private fun selectedVaultName(): String? {
        val item = comboModel.selectedItem as? String ?: return null
        val noneLabel = ObsidianBundle.message("settings.project.vault.none")
        return if (item == noneLabel) null else item
    }
}

package dev.jarviis.obsidian.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import dev.jarviis.obsidian.ObsidianBundle
import dev.jarviis.obsidian.vault.VaultManager
import javax.swing.JComponent

class ProjectSettingsConfigurable(private val project: Project) : Configurable {

    private val checkBoxes = mutableListOf<Pair<String, JBCheckBox>>()

    override fun getDisplayName(): String = ObsidianBundle.message("settings.project.display.name")

    override fun createComponent(): JComponent {
        checkBoxes.clear()
        val allVaults = service<VaultManager>().registeredVaults()
        val associated = ProjectVaultSettings.getInstance(project).associatedVaultNames.toSet()

        return panel {
            if (allVaults.isEmpty()) {
                row { cell(JBLabel(ObsidianBundle.message("vault.not.configured"))) }
            } else {
                group(ObsidianBundle.message("settings.vaults.label")) {
                    for (vault in allVaults) {
                        val cb = JBCheckBox(vault.name, vault.name in associated)
                        checkBoxes += vault.name to cb
                        row { cell(cb) }
                    }
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val current = ProjectVaultSettings.getInstance(project).associatedVaultNames.toSet()
        val panel = checkBoxes.filter { it.second.isSelected }.map { it.first }.toSet()
        return current != panel
    }

    override fun apply() {
        ProjectVaultSettings.getInstance(project).associatedVaultNames =
            checkBoxes.filter { it.second.isSelected }.map { it.first }
    }

    override fun reset() {
        val associated = ProjectVaultSettings.getInstance(project).associatedVaultNames.toSet()
        checkBoxes.forEach { (name, cb) -> cb.isSelected = name in associated }
    }
}

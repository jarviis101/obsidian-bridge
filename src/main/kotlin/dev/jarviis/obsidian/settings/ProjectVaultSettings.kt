package dev.jarviis.obsidian.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

class ProjectVaultState : BaseState() {
    /** Name of the vault associated with this project. */
    var activeVaultName by string()
    /** Absolute path to the vault root — kept in sync so the vault can be re-registered after a fresh IDE start. */
    var activeVaultPath by string()
}

/**
 * Project-level persistent settings: which vault is linked to this project (one-to-one).
 * Stored in `.idea/obsidian-lens.xml`.
 */
@State(name = "ObsidianLensProject", storages = [Storage("obsidian-lens.xml")])
@Service(Service.Level.PROJECT)
class ProjectVaultSettings : SimplePersistentStateComponent<ProjectVaultState>(ProjectVaultState()) {

    /** The name of the vault associated with this project, or null if none is selected. */
    var activeVaultName: String?
        get() = state.activeVaultName?.takeIf { it.isNotBlank() }
        set(value) { state.activeVaultName = value }

    /** The absolute path of the active vault, or null if none is selected. */
    var activeVaultPath: String?
        get() = state.activeVaultPath?.takeIf { it.isNotBlank() }
        set(value) { state.activeVaultPath = value }

    companion object {
        fun getInstance(project: Project): ProjectVaultSettings =
            project.getService(ProjectVaultSettings::class.java)
    }
}

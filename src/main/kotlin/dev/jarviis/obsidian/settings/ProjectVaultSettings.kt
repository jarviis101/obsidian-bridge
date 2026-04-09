package dev.jarviis.obsidian.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

class ProjectVaultState : BaseState() {
    /** Names of vaults (from AppVaultSettings) associated with this project. */
    var associatedVaultNames by list<String>()
}

/**
 * Project-level persistent settings: which vault(s) are linked to this project.
 * Stored in `.idea/obsidian-lens.xml`.
 */
@State(name = "ObsidianLensProject", storages = [Storage("obsidian-lens.xml")])
@Service(Service.Level.PROJECT)
class ProjectVaultSettings : SimplePersistentStateComponent<ProjectVaultState>(ProjectVaultState()) {

    var associatedVaultNames: List<String>
        get() = state.associatedVaultNames.toList()
        set(value) { state.associatedVaultNames = value.toMutableList() }

    companion object {
        fun getInstance(project: Project): ProjectVaultSettings =
            project.getService(ProjectVaultSettings::class.java)
    }
}

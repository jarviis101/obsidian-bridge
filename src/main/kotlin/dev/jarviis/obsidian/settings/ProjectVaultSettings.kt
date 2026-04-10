package dev.jarviis.obsidian.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import dev.jarviis.obsidian.model.VaultDescriptor

class ProjectVaultState : BaseState() {
    /** Per-project vault list, persisted as "name::path" entries. */
    var vaultEntries by list<String>()
}

/**
 * Project-level persistent settings: the vault list and active vault for this project.
 * Each project maintains its own independent vault list.
 * Stored in `.idea/obsidian-lens.xml`.
 */
@State(name = "ObsidianLensProject", storages = [Storage("obsidian-lens.xml")])
@Service(Service.Level.PROJECT)
class ProjectVaultSettings : SimplePersistentStateComponent<ProjectVaultState>(ProjectVaultState()) {

    /** All vaults registered for this project. */
    var vaults: List<VaultDescriptor>
        get() = state.vaultEntries.mapNotNull { parseEntry(it) }
        set(value) {
            state.vaultEntries = value.map { "${it.name}::${it.rootPathString}" }.toMutableList()
        }

    private fun parseEntry(entry: String): VaultDescriptor? {
        val idx = entry.indexOf("::")
        if (idx < 0) return null
        return VaultDescriptor(name = entry.substring(0, idx), rootPathString = entry.substring(idx + 2))
    }

    companion object {
        fun getInstance(project: Project): ProjectVaultSettings =
            project.getService(ProjectVaultSettings::class.java)
    }
}

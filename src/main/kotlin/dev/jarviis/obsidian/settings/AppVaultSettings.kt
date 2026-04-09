package dev.jarviis.obsidian.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import dev.jarviis.obsidian.model.VaultDescriptor

class AppVaultState : BaseState() {
    /** Persisted as a list of "name::path" entries to survive IDE restarts. */
    var vaultEntries by list<String>()
    var crossVaultSearch by property(false)
}

/**
 * Application-level persistent settings: registered vaults and global options.
 * Stored in `obsidian-bridge.xml` in the IDE config directory.
 */
@State(name = "ObsidianBridgeApp", storages = [Storage("obsidian-bridge.xml")])
@Service(Service.Level.APP)
class AppVaultSettings : SimplePersistentStateComponent<AppVaultState>(AppVaultState()) {

    var vaults: List<VaultDescriptor>
        get() = state.vaultEntries.mapNotNull { parseEntry(it) }
        set(value) {
            state.vaultEntries = value.map { "${it.name}::${it.rootPathString}" }.toMutableList()
        }

    var crossVaultSearch: Boolean
        get() = state.crossVaultSearch
        set(value) { state.crossVaultSearch = value }

    private fun parseEntry(entry: String): VaultDescriptor? {
        val idx = entry.indexOf("::")
        if (idx < 0) return null
        return VaultDescriptor(name = entry.substring(0, idx), rootPathString = entry.substring(idx + 2))
    }

    companion object {
        fun getInstance(): AppVaultSettings = service()
    }
}

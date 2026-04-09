package dev.jarviis.obsidian.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.jarviis.obsidian.model.VaultDescriptor
import dev.jarviis.obsidian.settings.AppVaultSettings
import dev.jarviis.obsidian.settings.ProjectVaultSettings
import dev.jarviis.obsidian.vault.VaultManager
import java.nio.file.Path
import java.nio.file.Paths

private val LOG = logger<ObsidianStartupActivity>()

/**
 * On project open:
 *  1. If the project already has a vault configured — ensure it is registered in
 *     [VaultManager] (it may be missing after a fresh IDE start / clean sandbox).
 *  2. Otherwise, scan the project directory for an Obsidian vault and auto-configure it.
 */
class ObsidianStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val manager = service<VaultManager>()
        val appSettings = AppVaultSettings.getInstance()
        val projectSettings = ProjectVaultSettings.getInstance(project)

        val savedName = projectSettings.activeVaultName
        val savedPath = projectSettings.activeVaultPath

        if (savedName != null && savedPath != null) {
            // Project already has a vault — make sure it is registered (survives fresh IDE starts)
            ensureRegistered(VaultDescriptor(name = savedName, rootPathString = savedPath), appSettings, manager)
            return
        }

        // No vault configured yet — try auto-detection
        val basePath = project.basePath ?: return
        LOG.info("ObsidianBridge: scanning for vault in '$basePath'")
        val detected = detectVault(Paths.get(basePath)) ?: run {
            LOG.info("ObsidianBridge: no vault found in '$basePath'")
            return
        }

        LOG.info("ObsidianBridge: auto-detected vault '${detected.name}' at ${detected.rootPathString}")
        ensureRegistered(detected, appSettings, manager)

        val registeredName = appSettings.vaults
            .firstOrNull { it.rootPathString == detected.rootPathString }?.name ?: detected.name
        projectSettings.activeVaultName = registeredName
        projectSettings.activeVaultPath = detected.rootPathString
        LOG.info("ObsidianBridge: project '${project.name}' auto-linked to vault '$registeredName'")
    }

    private fun ensureRegistered(descriptor: VaultDescriptor, appSettings: AppVaultSettings, manager: VaultManager) {
        if (!descriptor.isValid()) {
            LOG.warn("ObsidianBridge: vault path '${descriptor.rootPathString}' no longer exists, skipping")
            return
        }
        if (appSettings.vaults.none { it.rootPathString == descriptor.rootPathString }) {
            appSettings.vaults = appSettings.vaults + descriptor
            LOG.info("ObsidianBridge: re-registered vault '${descriptor.name}' in AppVaultSettings")
        }
        manager.registerVault(descriptor)
    }

    /**
     * Looks for a vault in:
     *  - [base] itself (the project root is the vault)
     *  - direct subdirectories of [base] (vault folder inside the project)
     */
    private fun detectVault(base: Path): VaultDescriptor? {
        val rootDescriptor = VaultDescriptor(name = base.fileName?.toString() ?: "vault", rootPathString = base.toString())
        if (rootDescriptor.hasObsidianConfig) return rootDescriptor

        val children = base.toFile().listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return null
        return children
            .map { VaultDescriptor(name = it.name, rootPathString = it.absolutePath) }
            .firstOrNull { it.hasObsidianConfig }
    }
}

package dev.jarviis.obsidian.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.jarviis.obsidian.settings.ProjectVaultSettings
import dev.jarviis.obsidian.vault.VaultManager
import dev.jarviis.obsidian.vault.detectVaultIn
import java.nio.file.Paths

private val LOG = logger<ObsidianStartupActivity>()

/**
 * On project open:
 *  1. If the project already has vaults configured — re-register them in [VaultManager]
 *     (they may be missing after a fresh IDE start / clean sandbox).
 *  2. Otherwise, scan the project directory for an Obsidian vault and auto-configure it.
 */
class ObsidianStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val manager = service<VaultManager>()
        val projectSettings = ProjectVaultSettings.getInstance(project)

        val savedVaults = projectSettings.vaults
        if (savedVaults.isNotEmpty()) {
            for (descriptor in savedVaults) {
                if (descriptor.isValid()) {
                    manager.registerVault(descriptor)
                }
            }
            return
        }

        val basePath = project.basePath ?: return
        LOG.info("ObsidianBridge: scanning for vault in '$basePath'")
        val detected = detectVaultIn(Paths.get(basePath)) ?: run {
            LOG.info("ObsidianBridge: no vault found in '$basePath'")
            return
        }

        LOG.info("ObsidianBridge: auto-detected vault '${detected.name}' at ${detected.rootPathString}")
        manager.registerVault(detected)
        projectSettings.vaults = listOf(detected)
        LOG.info("ObsidianBridge: project '${project.name}' auto-linked to vault '${detected.name}'")
    }
}

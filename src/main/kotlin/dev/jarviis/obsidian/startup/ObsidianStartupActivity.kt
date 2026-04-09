package dev.jarviis.obsidian.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.jarviis.obsidian.vault.VaultManager

/**
 * Eagerly initializes [VaultManager] when a project opens so vault indexing
 * begins immediately — before the user opens any tool window.
 */
class ObsidianStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Accessing the service triggers VaultManager.init → loadFromSettings() → async scan
        service<VaultManager>()
    }
}

package dev.jarviis.obsidian.startup

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiManager
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
        } else {
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

        // Vault indexing is async. Once done, restart code analysis so FoldingBuilder
        // re-runs on all open .md files that were opened before the index was ready.
        val listener = object : VaultManager.VaultChangeListener {
            override fun onVaultChanged() {
                manager.removeChangeListener(this)
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    val analyzer = DaemonCodeAnalyzer.getInstance(project)
                    val psiManager = PsiManager.getInstance(project)
                    FileEditorManager.getInstance(project).openFiles
                        .filter { it.name.endsWith(".md") }
                        .mapNotNull { psiManager.findFile(it) }
                        .forEach { analyzer.restart(it, "vault indexed") }
                }
            }
        }
        manager.addChangeListener(listener)
    }
}

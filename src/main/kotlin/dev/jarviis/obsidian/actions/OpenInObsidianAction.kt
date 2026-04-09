package dev.jarviis.obsidian.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import dev.jarviis.obsidian.ObsidianBundle
import dev.jarviis.obsidian.vault.VaultManager
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Paths

class OpenInObsidianAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && file.name.endsWith(".md")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val path = Paths.get(file.path)

        val manager = service<VaultManager>()
        val index = manager.indexForPath(path) ?: return
        val note = index.findByPath(path) ?: return

        if (!Desktop.isDesktopSupported()) {
            Messages.showWarningDialog(
                e.project,
                ObsidianBundle.message("action.open.in.obsidian.unsupported"),
                ObsidianBundle.message("action.open.in.obsidian.text"),
            )
            return
        }

        val vaultName = URLEncoder.encode(note.vaultName, Charsets.UTF_8).replace("+", "%20")
        val filePath = URLEncoder.encode(note.relativePath.toString(), Charsets.UTF_8).replace("+", "%20")
        val uri = URI("obsidian://open?vault=$vaultName&file=$filePath")
        Desktop.getDesktop().browse(uri)
    }
}

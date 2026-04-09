package dev.jarviis.obsidian.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import dev.jarviis.obsidian.ObsidianBundle
import dev.jarviis.obsidian.parser.TemplateEngine
import dev.jarviis.obsidian.settings.ProjectVaultSettings
import dev.jarviis.obsidian.vault.VaultManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val LOG = logger<DailyNoteAction>()

/** Default values matching Obsidian's built-in daily notes plugin. */
private const val DEFAULT_FORMAT = "yyyy-MM-dd"
private const val DEFAULT_FOLDER = ""

class DailyNoteAction : AnAction() {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project ?: run { e.presentation.isEnabledAndVisible = false; return }
        val vaultNames = ProjectVaultSettings.getInstance(project).associatedVaultNames
        e.presentation.isEnabledAndVisible = vaultNames.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val manager = service<VaultManager>()

        val vaultNames = ProjectVaultSettings.getInstance(project).associatedVaultNames
        val descriptor = vaultNames.firstOrNull()
            ?.let { name -> manager.registeredVaults().firstOrNull { it.name == name } }
            ?: run {
                Messages.showWarningDialog(
                    project,
                    ObsidianBundle.message("vault.not.configured"),
                    ObsidianBundle.message("action.daily.note.text"),
                )
                return
            }

        val config = loadDailyNotesConfig(descriptor.rootPath)
        val folder = config["folder"]?.jsonPrimitive?.content ?: DEFAULT_FOLDER
        val format = config["format"]?.jsonPrimitive?.content?.toJavaDatePattern() ?: DEFAULT_FORMAT
        val templatePath = config["template"]?.jsonPrimitive?.content

        val today = LocalDate.now()
        val fileName = today.format(DateTimeFormatter.ofPattern(format)) + ".md"
        val notePath = if (folder.isBlank())
            descriptor.rootPath.resolve(fileName)
        else
            descriptor.rootPath.resolve(folder).resolve(fileName)

        if (!Files.exists(notePath)) {
            createDailyNote(notePath, today.format(DateTimeFormatter.ofPattern(format)), templatePath, descriptor.rootPath)
        }

        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(notePath) ?: run {
            LOG.warn("Could not find VirtualFile for $notePath")
            return
        }
        FileEditorManager.getInstance(project).openFile(vFile, true)
    }

    private fun loadDailyNotesConfig(vaultRoot: Path): JsonObject {
        val configFile = vaultRoot.resolve(".obsidian").resolve("daily-notes.json")
        if (!Files.exists(configFile)) {
            LOG.info(ObsidianBundle.message("vault.daily.notes.config.missing"))
            return JsonObject(emptyMap())
        }
        return try {
            json.parseToJsonElement(Files.readString(configFile)).jsonObject
        } catch (ex: Exception) {
            LOG.warn("Failed to parse daily-notes.json: ${ex.message}")
            JsonObject(emptyMap())
        }
    }

    private fun createDailyNote(path: Path, title: String, templatePath: String?, vaultRoot: Path) {
        val templateContent = templatePath?.let { loadTemplate(it, vaultRoot) }
        val content = if (templateContent != null)
            TemplateEngine.render(templateContent, title)
        else
            "# $title\n"

        WriteAction.runAndWait<Exception> {
            Files.createDirectories(path.parent)
            Files.writeString(path, content)
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        }
    }

    private fun loadTemplate(templateRef: String, vaultRoot: Path): String? {
        val templateFile = vaultRoot.resolve("$templateRef.md")
        if (!Files.exists(templateFile)) {
            LOG.warn(ObsidianBundle.message("vault.template.missing", templateRef))
            return null
        }
        return try { Files.readString(templateFile) } catch (ex: Exception) { null }
    }

    /** Convert Obsidian moment.js date tokens to java.time DateTimeFormatter pattern. */
    private fun String.toJavaDatePattern(): String =
        this.replace("YYYY", "yyyy").replace("DD", "dd").replace("D", "d")
}

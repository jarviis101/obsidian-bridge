package dev.jarviis.obsidian.toolwindow.backlinks

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import dev.jarviis.obsidian.model.ObsidianNote
import dev.jarviis.obsidian.vault.VaultManager
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.BoxLayout
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

class BacklinksPanel(private val project: Project) : JBPanel<BacklinksPanel>(BorderLayout()) {

    // ── Outgoing links (this note → others) ────────────────────────────────
    private val outgoingLabel = JBLabel("Links").apply {
        border = javax.swing.BorderFactory.createEmptyBorder(4, 4, 2, 4)
        font = font.deriveFont(java.awt.Font.BOLD)
    }
    private val outgoingModel = CollectionListModel<ObsidianNote>()
    private val outgoingList = JBList(outgoingModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = BacklinkCellRenderer()
        addListSelectionListener { if (!it.valueIsAdjusting) openNote(selectedValue) }
    }

    // ── Incoming backlinks (others → this note) ─────────────────────────────
    private val backlinksLabel = JBLabel("Backlinks").apply {
        border = javax.swing.BorderFactory.createEmptyBorder(6, 4, 2, 4)
        font = font.deriveFont(java.awt.Font.BOLD)
    }
    private val backlinksModel = CollectionListModel<ObsidianNote>()
    private val backlinksList = JBList(backlinksModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = BacklinkCellRenderer()
        addListSelectionListener { if (!it.valueIsAdjusting) openNote(selectedValue) }
    }

    // ── Status (shown when no md file is open) ──────────────────────────────
    private val statusLabel = JBLabel("Open a Markdown file to see links").apply {
        horizontalAlignment = JBLabel.CENTER
    }

    init {
        val content = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(outgoingLabel)
            add(JBScrollPane(outgoingList).apply {
                border = javax.swing.BorderFactory.createEmptyBorder(0, 4, 0, 4)
                preferredSize = java.awt.Dimension(Int.MAX_VALUE, 220)
                maximumSize = java.awt.Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            })
            add(backlinksLabel)
            add(JBScrollPane(backlinksList).apply {
                border = javax.swing.BorderFactory.createEmptyBorder(0, 4, 4, 4)
                preferredSize = java.awt.Dimension(Int.MAX_VALUE, 160)
                maximumSize = java.awt.Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            })
        }

        add(statusLabel, BorderLayout.NORTH)
        add(JBScrollPane(content).apply {
            border = null
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)

        // Update panel when the active editor changes
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                    updateFor(event.newFile)
                }
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    updateFor(file)
                }
            }
        )

        // Reload when vault index changes
        service<VaultManager>().addChangeListener {
            SwingUtilities.invokeLater {
                updateFor(FileEditorManager.getInstance(project).selectedFiles.firstOrNull())
            }
        }

        // Show current file immediately
        updateFor(FileEditorManager.getInstance(project).selectedFiles.firstOrNull())
    }

    fun updateFor(file: VirtualFile?) {
        if (file == null || !file.name.endsWith(".md")) {
            statusLabel.text = "Open a Markdown file to see links"
            statusLabel.isVisible = true
            outgoingModel.removeAll()
            backlinksModel.removeAll()
            outgoingLabel.text = "Links"
            backlinksLabel.text = "Backlinks"
            return
        }

        statusLabel.isVisible = false

        val manager = service<VaultManager>()
        val path = Paths.get(file.path)
        val projectIndex = manager.indexForProject(project)

        // If a vault is configured for the project, use it only when the file belongs to it.
        // If no vault is configured, fall back to whichever vault owns the file.
        val index = if (projectIndex != null) {
            projectIndex.takeIf { path.startsWith(it.descriptor.rootPath) }
        } else {
            manager.indexForPath(path)
        } ?: run {
            outgoingLabel.text = "Links"
            backlinksLabel.text = "Backlinks"
            outgoingModel.removeAll()
            backlinksModel.removeAll()
            return
        }

        // Outgoing: resolve each wiki-link in this note → unique target notes (by path)
        val currentNote = index.findByPath(path)
        val resolvedLinks = currentNote?.outgoingLinks
            ?.mapNotNull { index.resolve(it.target, path) }
            ?: emptyList()
        val outgoing = resolvedLinks.distinctBy { it.path }.sortedBy { it.name }

        // Incoming: notes that link to this file
        val incoming = index.backlinksFor(path)

        outgoingModel.replaceAll(outgoing)
        backlinksModel.replaceAll(incoming)

        val totalLinks = currentNote?.outgoingLinks?.size ?: 0
        outgoingLabel.text = if (totalLinks == outgoing.size)
            "Links (${outgoing.size})"
        else
            "Links (${outgoing.size} notes · ${totalLinks} links)"
        backlinksLabel.text = "Backlinks (${incoming.size})"
    }

    private fun openNote(note: ObsidianNote?) {
        note ?: return
        val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByNioFile(note.path) ?: return
        FileEditorManager.getInstance(project).openFile(vFile, true)
    }
}

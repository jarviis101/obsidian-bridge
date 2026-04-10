package dev.jarviis.obsidian.vault

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.nio.file.Path
import java.nio.file.Paths

private val LOG = logger<VaultWatcher>()

/**
 * Listens for VFS events and drives incremental [VaultIndex] updates.
 * Registered by [VaultManager] via [com.intellij.openapi.vfs.VirtualFileManager.addAsyncFileListener].
 */
class VaultWatcher(private val manager: VaultManager) : AsyncFileListener {

    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
        val relevant = events.mapNotNull { event -> classify(event) }
        if (relevant.isEmpty()) return null

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                for ((kind, path) in relevant) {
                    manager.handleFileEvent(kind, path)
                }
            }
        }
    }

    private fun classify(event: VFileEvent): Pair<FileEventKind, Path>? {
        val path = event.path.let { Paths.get(it) }
        if (!path.fileName.toString().endsWith(".md")) return null
        if (!manager.isInsideRegisteredVault(path)) return null

        return when (event) {
            is VFileCreateEvent -> FileEventKind.CREATED to path
            is VFileContentChangeEvent -> FileEventKind.CHANGED to path
            is VFileDeleteEvent -> FileEventKind.DELETED to path
            is VFileMoveEvent -> FileEventKind.CHANGED to Paths.get(event.newPath)
            is VFilePropertyChangeEvent -> if (event.propertyName == "name") FileEventKind.CHANGED to path else null
            else -> null
        }
    }

    enum class FileEventKind { CREATED, CHANGED, DELETED }
}

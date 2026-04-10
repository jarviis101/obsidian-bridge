package dev.jarviis.obsidian.vault

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import dev.jarviis.obsidian.settings.ProjectVaultSettings
import com.intellij.util.concurrency.AppExecutorUtil
import dev.jarviis.obsidian.model.ObsidianNote
import dev.jarviis.obsidian.model.VaultDescriptor
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val LOG = logger<VaultManager>()

/**
 * Application-level service — single source of truth for all registered vaults.
 *
 * Lifecycle:
 *  1. Settings configurable calls [registerVault] / [removeVault] when the user
 *     adds or removes a vault.
 *  2. On registration, an async background scan populates [VaultIndex].
 *  3. [VaultWatcher] feeds incremental updates back via [handleFileEvent].
 */
@Service(Service.Level.APP)
class VaultManager {

    private val indices = ConcurrentHashMap<String, VaultIndex>()
    private val descriptors = CopyOnWriteArrayList<VaultDescriptor>()
    private val listeners = CopyOnWriteArrayList<VaultChangeListener>()

    init {
        VirtualFileManager.getInstance().addAsyncFileListener(VaultWatcher(this), ApplicationManager.getApplication())
    }

    fun registeredVaults(): List<VaultDescriptor> = descriptors.toList()

    fun registerVault(descriptor: VaultDescriptor) {
        if (descriptors.none { it.rootPathString == descriptor.rootPathString }) {
            descriptors += descriptor
            rebuildIndexAsync(descriptor)
        }
    }

    fun removeVault(name: String) {
        val path = descriptors.firstOrNull { it.name == name }?.rootPathString
        descriptors.removeIf { it.name == name }
        if (path != null) indices.remove(path)
        notifyListeners()
    }

    fun replaceVaults(newDescriptors: List<VaultDescriptor>) {
        val existingPaths = descriptors.map { it.rootPathString }.toSet()
        val incomingPaths = newDescriptors.map { it.rootPathString }.toSet()

        descriptors.filter { it.rootPathString !in incomingPaths }
            .forEach { removeVault(it.name) }

        for (d in newDescriptors) {
            if (d.rootPathString !in existingPaths) registerVault(d)
        }
    }

    fun indexForPath(path: Path): VaultIndex? =
        indices.values.firstOrNull { path.startsWith(it.descriptor.rootPath) }

    fun isInsideRegisteredVault(path: Path): Boolean =
        descriptors.any { path.startsWith(it.rootPath) }

    fun indexForProject(project: Project): VaultIndex? {
        val path = ProjectVaultSettings.getInstance(project).vaults.firstOrNull()?.rootPathString ?: return null
        return indices[path]
    }

    fun resolve(target: String, contextPath: Path? = null): ObsidianNote? {
        val normalizedTarget = target.removeSuffix(".md").replace('\\', '/')
        val contextIndex = contextPath?.let { indexForPath(it) }
        return contextIndex?.resolve(normalizedTarget, contextPath)
            ?: indices.values.firstNotNullOfOrNull { it.resolve(normalizedTarget) }
    }

    fun resolveInProject(target: String, project: Project, contextPath: Path? = null): ObsidianNote? {
        val index = indexForProject(project) ?: return null
        val normalizedTarget = target.removeSuffix(".md").replace('\\', '/')
        return index.resolve(normalizedTarget, contextPath)
    }

    fun addChangeListener(listener: VaultChangeListener) {
        listeners += listener
    }

    private fun notifyListeners() {
        listeners.forEach { it.onVaultChanged() }
    }

    private fun rebuildIndexAsync(descriptor: VaultDescriptor) {
        AppExecutorUtil.getAppExecutorService().submit {
            LOG.info("ObsidianBridge: scanning vault '${descriptor.name}' at ${descriptor.rootPathString}")
            try {
                val notes = VaultScanner.scanAll(descriptor)
                val index = VaultIndex(descriptor)
                index.rebuild(notes)
                indices[descriptor.rootPathString] = index
                LOG.info("ObsidianBridge: vault '${descriptor.name}' indexed — ${notes.size} note(s)")
                notifyListeners()
            } catch (e: Exception) {
                LOG.error("ObsidianBridge: failed to index vault '${descriptor.name}'", e)
            }
        }
    }

    internal fun handleFileEvent(kind: VaultWatcher.FileEventKind, path: Path) {
        val index = indexForPath(path) ?: return
        when (kind) {
            VaultWatcher.FileEventKind.CREATED,
            VaultWatcher.FileEventKind.CHANGED -> {
                val note = VaultScanner.parseNote(path, index.descriptor.rootPath) ?: return
                index.upsert(note)
                notifyListeners()
            }
            VaultWatcher.FileEventKind.DELETED -> {
                index.remove(path)
                notifyListeners()
            }
        }
    }

    fun interface VaultChangeListener {
        fun onVaultChanged()
    }
}

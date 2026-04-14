package dev.jarviis.obsidian.vault

import com.intellij.openapi.diagnostic.logger
import dev.jarviis.obsidian.model.ObsidianNote
import dev.jarviis.obsidian.model.VaultDescriptor
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val LOG = logger<VaultIndex>()

/**
 * Thread-safe in-memory index for a single Obsidian vault.
 *
 * Read operations are safe from any thread.
 * Write operations (rebuild/update) are serialized via [ReentrantReadWriteLock].
 */
class VaultIndex(val descriptor: VaultDescriptor) {

    private val lock = ReentrantReadWriteLock()

    private val notesByPath = mutableMapOf<Path, ObsidianNote>()
    private val notesByName = mutableMapOf<String, MutableList<ObsidianNote>>()
    private val backlinks = mutableMapOf<Path, MutableSet<ObsidianNote>>()

    fun allNotes(): List<ObsidianNote> = lock.read { notesByPath.values.toList() }

    fun findByPath(path: Path): ObsidianNote? = lock.read { notesByPath[path] }

    fun resolve(target: String, contextPath: Path? = null): ObsidianNote? = lock.read {
        val normalized = target.removeSuffix(".md").replace('\\', '/')

        if (contextPath != null && (normalized.startsWith("./") || normalized.startsWith("../"))) {
            val contextDir = contextPath.parent ?: return@read null
            val resolved = contextDir.resolve(normalized).normalize()
            notesByPath[resolved]?.let { return@read it }
            notesByPath[contextDir.resolve("$normalized.md").normalize()]?.let { return@read it }
        }

        val key = normalized.lowercase()
        val candidates = notesByName[key] ?: return@read null
        when {
            candidates.size == 1 -> candidates.first()
            contextPath != null -> candidates.minByOrNull { note ->
                val sameFolder = note.path.parent == contextPath.parent
                val commonPrefix = commonPrefixLength(note.path, contextPath)
                val pathLen = note.relativePath.nameCount
                if (sameFolder) -1000 + pathLen else -commonPrefix * 10 + pathLen
            }
            else -> candidates.minByOrNull { it.relativePath.nameCount }
        }
    }

    private fun commonPrefixLength(a: Path, b: Path): Int {
        var count = 0
        val limit = minOf(a.nameCount, b.nameCount)
        while (count < limit && a.getName(count) == b.getName(count)) count++
        return count
    }

    fun backlinksFor(path: Path): List<ObsidianNote> = lock.read {
        backlinks[path]?.toList() ?: emptyList()
    }

    fun rebuild(notes: List<ObsidianNote>) = lock.write {
        notesByPath.clear()
        notesByName.clear()
        backlinks.clear()
        for (note in notes) {
            notesByPath[note.path] = note
            for (key in note.allKeys()) {
                notesByName.getOrPut(key) { mutableListOf() }.add(note)
            }
        }
        var resolved = 0
        var unresolved = 0
        val unresolvedSamples = mutableListOf<String>()
        for (note in notes) {
            for (link in note.outgoingLinks) {
                val target = resolve(link.target, note.path)
                if (target == null) {
                    unresolved++
                    if (unresolvedSamples.size < 5) unresolvedSamples += "'${link.target}' in ${note.name}"
                } else {
                    backlinks.getOrPut(target.path) { mutableSetOf() }.add(note)
                    resolved++
                }
            }
        }
        LOG.info("ObsidianBridge: backlinks built — $resolved resolved, $unresolved unresolved")
        if (unresolvedSamples.isNotEmpty()) LOG.info("ObsidianBridge: unresolved samples: $unresolvedSamples")
    }

    fun upsert(note: ObsidianNote) = lock.write {
        removeFromIndices(note.path)
        indexNote(note)
    }

    fun remove(path: Path) = lock.write {
        removeFromIndices(path)
        backlinks.remove(path)
    }

    private fun indexNote(note: ObsidianNote) {
        notesByPath[note.path] = note
        for (key in note.allKeys()) {
            notesByName.getOrPut(key) { mutableListOf() }.add(note)
        }
        for (link in note.outgoingLinks) {
            val target = resolve(link.target, note.path) ?: continue
            backlinks.getOrPut(target.path) { mutableSetOf() }.add(note)
        }
    }

    private fun removeFromIndices(path: Path) {
        val existing = notesByPath.remove(path) ?: return
        for (key in existing.allKeys()) {
            notesByName[key]?.remove(existing)
        }
        for (link in existing.outgoingLinks) {
            val target = resolve(link.target, existing.path) ?: continue
            backlinks[target.path]?.remove(existing)
        }
    }

    private fun ObsidianNote.allKeys(): List<String> = buildList {
        val relativeNoExt = relativePath.toString().removeSuffix(".md").replace('\\', '/')
        val parts = relativeNoExt.split('/')
        for (startIdx in 0 until parts.size) {
            add(parts.subList(startIdx, parts.size).joinToString("/").lowercase())
        }
        addAll(frontmatter.aliases.map { it.lowercase() })
    }
}

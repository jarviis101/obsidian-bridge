package dev.jarviis.obsidian.vault

import dev.jarviis.obsidian.model.ObsidianNote
import dev.jarviis.obsidian.model.VaultDescriptor
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe in-memory index for a single Obsidian vault.
 *
 * Read operations are safe from any thread.
 * Write operations (rebuild/update) are serialized via [ReentrantReadWriteLock].
 */
class VaultIndex(val descriptor: VaultDescriptor) {

    private val lock = ReentrantReadWriteLock()

    // path → note
    private val notesByPath = mutableMapOf<Path, ObsidianNote>()

    // lowercased name/alias → list of notes (multiple notes can share an alias)
    private val notesByName = mutableMapOf<String, MutableList<ObsidianNote>>()

    // note path → set of notes that link TO it (backlinks)
    private val backlinks = mutableMapOf<Path, MutableSet<ObsidianNote>>()

    // ── Read API ──────────────────────────────────────────────────────────────

    fun allNotes(): List<ObsidianNote> = lock.read { notesByPath.values.toList() }

    fun noteCount(): Int = lock.read { notesByPath.size }

    fun findByPath(path: Path): ObsidianNote? = lock.read { notesByPath[path] }

    /**
     * Resolve a wiki-link target using Obsidian's rules:
     * 1. Relative path (./foo, ../foo) — resolved against the context file's directory
     * 2. Name / vault-relative path lookup — case-insensitive, prefer same-folder
     */
    fun resolve(target: String, contextPath: Path? = null): ObsidianNote? = lock.read {
        val normalized = target.removeSuffix(".md").replace('\\', '/')

        // 1. Relative path resolution
        if (contextPath != null && (normalized.startsWith("./") || normalized.startsWith("../"))) {
            val contextDir = contextPath.parent ?: return@read null
            val resolved = contextDir.resolve(normalized).normalize()
            notesByPath[resolved]?.let { return@read it }
            notesByPath[contextDir.resolve("$normalized.md").normalize()]?.let { return@read it }
        }

        // 2. Name / vault-relative path lookup
        val key = normalized.lowercase()
        val candidates = notesByName[key] ?: return@read null
        when {
            candidates.size == 1 -> candidates.first()
            contextPath != null -> candidates.minByOrNull { note ->
                val sameFolder = note.path.parent == contextPath.parent
                val pathLen = note.relativePath.nameCount
                if (sameFolder) -1000 + pathLen else pathLen
            }
            else -> candidates.minByOrNull { it.relativePath.nameCount }
        }
    }

    fun backlinksFor(note: ObsidianNote): List<ObsidianNote> = lock.read {
        backlinks[note.path]?.toList() ?: emptyList()
    }

    fun backlinksFor(path: Path): List<ObsidianNote> = lock.read {
        backlinks[path]?.toList() ?: emptyList()
    }

    // ── Write API (called from VaultManager background thread) ───────────────

    /** Fully replace the index with a fresh scan result. */
    fun rebuild(notes: List<ObsidianNote>) = lock.write {
        notesByPath.clear()
        notesByName.clear()
        backlinks.clear()
        // Pass 1: register all notes so resolve() works for every note
        for (note in notes) {
            notesByPath[note.path] = note
            for (key in note.allKeys()) {
                notesByName.getOrPut(key) { mutableListOf() }.add(note)
            }
        }
        // Pass 2: build backlinks now that all targets are resolvable
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
        val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(VaultIndex::class.java)
        LOG.info("ObsidianBridge: backlinks built — $resolved resolved, $unresolved unresolved")
        if (unresolvedSamples.isNotEmpty()) LOG.info("ObsidianBridge: unresolved samples: $unresolvedSamples")
    }

    /** Add or update a single note (called when a file changes). */
    fun upsert(note: ObsidianNote) = lock.write {
        removeFromIndices(note.path)
        indexNote(note)
    }

    /** Remove a note from the index (called when a file is deleted). */
    fun remove(path: Path) = lock.write {
        removeFromIndices(path)
        backlinks.remove(path)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

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

    /**
     * All lookup keys for a note.
     *
     * Obsidian resolves `[[folder/note]]` by finding any note whose vault-relative path
     * ENDS WITH `folder/note.md`. We therefore index every trailing sub-path.
     *
     * Example: `modules/platform/platform.md` produces keys:
     *   "platform"                  ← bare filename
     *   "platform/platform"         ← 2-component suffix
     *   "modules/platform/platform" ← full relative path
     */
    private fun ObsidianNote.allKeys(): List<String> = buildList {
        val relativeNoExt = relativePath.toString().removeSuffix(".md").replace('\\', '/')
        val parts = relativeNoExt.split('/')
        for (startIdx in 0 until parts.size) {
            add(parts.subList(startIdx, parts.size).joinToString("/").lowercase())
        }
        addAll(frontmatter.aliases.map { it.lowercase() })
    }
}

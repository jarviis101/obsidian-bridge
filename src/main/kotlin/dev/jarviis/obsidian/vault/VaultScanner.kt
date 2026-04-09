package dev.jarviis.obsidian.vault

import com.intellij.openapi.diagnostic.logger
import dev.jarviis.obsidian.model.Frontmatter
import dev.jarviis.obsidian.model.ObsidianNote
import dev.jarviis.obsidian.model.VaultDescriptor
import dev.jarviis.obsidian.parser.FrontmatterParser
import dev.jarviis.obsidian.parser.WikiLinkParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<VaultScanner>()

/**
 * Discovers and parses all `.md` files in a vault, skipping `.obsidian/` and
 * any paths excluded by the user's ignore filters (best-effort).
 */
object VaultScanner {

    fun scanAll(descriptor: VaultDescriptor): List<ObsidianNote> {
        if (!descriptor.isValid()) {
            LOG.warn("Vault root not valid: ${descriptor.rootPathString}")
            return emptyList()
        }
        val root = descriptor.rootPath
        val notes = mutableListOf<ObsidianNote>()
        Files.walk(root).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".md") }
                .filter { !isIgnored(it, root) }
                .forEach { path ->
                    parseNote(path, root)?.let { notes += it }
                }
        }
        LOG.info("Scanned vault '${descriptor.name}': ${notes.size} notes")
        return notes
    }

    fun parseNote(path: Path, vaultRoot: Path): ObsidianNote? {
        return try {
            val text = Files.readString(path)
            val frontmatter = FrontmatterParser.parse(text)
            val bodyStart = FrontmatterParser.frontmatterLength(text)
            val body = if (bodyStart < text.length) text.substring(bodyStart) else text
            val links = WikiLinkParser.parse(body)
            ObsidianNote(
                path = path,
                vaultRoot = vaultRoot,
                frontmatter = frontmatter,
                outgoingLinks = links,
            )
        } catch (e: IOException) {
            LOG.warn("Failed to read note: $path — ${e.message}")
            null
        }
    }

    private fun isIgnored(path: Path, vaultRoot: Path): Boolean {
        val relative = vaultRoot.relativize(path)
        // Always skip the .obsidian config directory
        return relative.firstOrNull()?.toString() == ".obsidian"
    }
}

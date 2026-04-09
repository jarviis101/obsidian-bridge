package dev.jarviis.obsidian.model

import java.nio.file.Path

/**
 * Represents a single note in an Obsidian vault.
 *
 * @param path      Absolute path to the `.md` file.
 * @param vaultRoot Absolute path to the vault root directory.
 * @param frontmatter Parsed YAML frontmatter (or [Frontmatter.EMPTY]).
 * @param outgoingLinks All wiki-links found in the note body.
 */
data class ObsidianNote(
    val path: Path,
    val vaultRoot: Path,
    val frontmatter: Frontmatter = Frontmatter.EMPTY,
    val outgoingLinks: List<WikiLink> = emptyList(),
) {
    /** File name without the `.md` extension, e.g. "My Note" */
    val name: String get() = path.fileName.toString().removeSuffix(".md")

    /** Path relative to the vault root, e.g. "Daily Notes/2024-01-01.md" */
    val relativePath: Path get() = vaultRoot.relativize(path)

    /** All names this note can be referenced by (filename + frontmatter aliases) */
    val resolvedNames: List<String> get() = buildList {
        add(name)
        addAll(frontmatter.aliases)
    }

    /** Vault name derived from the vault root directory name */
    val vaultName: String get() = vaultRoot.fileName.toString()
}

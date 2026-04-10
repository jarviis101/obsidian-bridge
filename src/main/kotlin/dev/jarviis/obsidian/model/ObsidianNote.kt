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
    val name: String get() = path.fileName.toString().removeSuffix(".md")
    val relativePath: Path get() = vaultRoot.relativize(path)
    val vaultName: String get() = vaultRoot.fileName.toString()
}

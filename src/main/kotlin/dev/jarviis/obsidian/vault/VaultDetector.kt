package dev.jarviis.obsidian.vault

import dev.jarviis.obsidian.model.VaultDescriptor
import java.nio.file.Path

/**
 * Scans [base] and its immediate non-hidden subdirectories for an Obsidian vault
 * (a directory containing a `.obsidian/` folder).
 *
 * Resolution order:
 *  1. [base] itself is a vault root.
 *  2. First direct subdirectory of [base] that has `.obsidian/`.
 */
fun detectVaultIn(base: Path): VaultDescriptor? {
    val rootDescriptor = VaultDescriptor(name = base.fileName?.toString() ?: "vault", rootPathString = base.toString())
    if (rootDescriptor.hasObsidianConfig) return rootDescriptor

    val children = base.toFile().listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return null
    return children
        .map { VaultDescriptor(name = it.name, rootPathString = it.absolutePath) }
        .firstOrNull { it.hasObsidianConfig }
}

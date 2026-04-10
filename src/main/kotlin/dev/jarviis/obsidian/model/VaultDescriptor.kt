package dev.jarviis.obsidian.model

import java.nio.file.Path
import java.nio.file.Paths

/** Lightweight descriptor of a registered vault — serializable, no VirtualFile references. */
data class VaultDescriptor(
    val name: String,
    val rootPathString: String,
) {
    val rootPath: Path get() = Paths.get(rootPathString)
    val obsidianDir: Path get() = rootPath.resolve(".obsidian")
    val hasObsidianConfig: Boolean get() = obsidianDir.toFile().isDirectory

    fun isValid(): Boolean = rootPath.toFile().isDirectory
}

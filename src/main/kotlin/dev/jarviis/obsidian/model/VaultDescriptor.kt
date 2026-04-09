package dev.jarviis.obsidian.model

import java.nio.file.Path
import java.nio.file.Paths

/** Lightweight descriptor of a registered vault — serializable, no VirtualFile references. */
data class VaultDescriptor(
    val name: String,
    /** Absolute path string — stored as String so it survives IDE restarts. */
    val rootPathString: String,
) {
    val rootPath: Path get() = Paths.get(rootPathString)
    val obsidianDir: Path get() = rootPath.resolve(".obsidian")

    /** Returns true if the root path exists as a directory (vault doesn't require .obsidian/ to be usable). */
    fun isValid(): Boolean = rootPath.toFile().isDirectory

    /** True when this is a full Obsidian vault with .obsidian/ config directory. */
    val hasObsidianConfig: Boolean get() = obsidianDir.toFile().isDirectory
}

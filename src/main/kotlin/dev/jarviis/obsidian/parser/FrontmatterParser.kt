package dev.jarviis.obsidian.parser

import com.intellij.openapi.diagnostic.logger
import dev.jarviis.obsidian.model.Frontmatter
import org.yaml.snakeyaml.Yaml

private val LOG = logger<FrontmatterParser>()

/**
 * Stateless YAML frontmatter parser.
 * Uses SnakeYAML which is bundled with the IntelliJ Platform — no extra dependency.
 */
object FrontmatterParser {

    private val FRONTMATTER_REGEX = Regex("""^---\r?\n(.*?)\r?\n---\r?\n?""", RegexOption.DOT_MATCHES_ALL)

    fun extractRaw(text: String): String? =
        FRONTMATTER_REGEX.find(text)?.groupValues?.get(1)

    fun frontmatterLength(text: String): Int =
        FRONTMATTER_REGEX.find(text)?.value?.length ?: 0

    fun parse(text: String): Frontmatter {
        val raw = extractRaw(text) ?: return Frontmatter.EMPTY
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = Yaml().load<Map<String, Any>>(raw) ?: return Frontmatter.EMPTY
            Frontmatter(aliases = parseStringList(map["aliases"]))
        } catch (e: Exception) {
            LOG.warn("Failed to parse frontmatter: ${e.message}")
            Frontmatter.EMPTY
        }
    }

    private fun parseStringList(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is List<*> -> value.filterIsInstance<String>()
        is String -> listOf(value)
        else -> emptyList()
    }
}

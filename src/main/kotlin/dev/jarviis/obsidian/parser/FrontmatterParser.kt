package dev.jarviis.obsidian.parser

import com.intellij.openapi.diagnostic.logger
import dev.jarviis.obsidian.model.Frontmatter
import org.yaml.snakeyaml.Yaml
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val LOG = logger<FrontmatterParser>()

private val DATE_FORMATS = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE,           // 2024-01-15
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
    DateTimeFormatter.ofPattern("yyyy.MM.dd"),
)

/**
 * Stateless YAML frontmatter parser.
 * Uses SnakeYAML which is bundled with the IntelliJ Platform — no extra dependency.
 */
object FrontmatterParser {

    private val FRONTMATTER_REGEX = Regex("""^---\r?\n(.*?)\r?\n---\r?\n?""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Returns the raw YAML block string from [text] (without delimiters),
     * or null if no frontmatter is present.
     */
    fun extractRaw(text: String): String? =
        FRONTMATTER_REGEX.find(text)?.groupValues?.get(1)

    /**
     * Returns the character length of the full frontmatter block (including delimiters),
     * so callers can skip past it when scanning the body.
     */
    fun frontmatterLength(text: String): Int =
        FRONTMATTER_REGEX.find(text)?.value?.length ?: 0

    /** Parse frontmatter from note text; returns [Frontmatter.EMPTY] on missing or malformed YAML. */
    fun parse(text: String): Frontmatter {
        val raw = extractRaw(text) ?: return Frontmatter.EMPTY
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = Yaml().load<Map<String, Any>>(raw) ?: return Frontmatter.EMPTY
            buildFrontmatter(map)
        } catch (e: Exception) {
            LOG.warn("Failed to parse frontmatter: ${e.message}")
            Frontmatter.EMPTY
        }
    }

    private fun buildFrontmatter(map: Map<String, Any>): Frontmatter {
        val tags = parseTags(map["tags"])
        val aliases = parseStringList(map["aliases"])
        val title = map["title"]?.toString()
        val date = parseDate(map["date"] ?: map["created"])
        val extra = map.filterKeys { it !in setOf("tags", "aliases", "title", "date", "created") }
        return Frontmatter(tags = tags, aliases = aliases, date = date, title = title, extra = extra)
    }

    private fun parseTags(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is List<*> -> value.filterIsInstance<String>().map { it.trimStart('#').lowercase() }
        is String -> value.split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.trimStart('#').lowercase() }
        else -> emptyList()
    }

    private fun parseStringList(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is List<*> -> value.filterIsInstance<String>()
        is String -> listOf(value)
        else -> emptyList()
    }

    private fun parseDate(value: Any?): LocalDate? {
        if (value == null) return null
        // SnakeYAML may parse ISO dates directly into java.util.Date
        if (value is java.util.Date) {
            return value.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate()
        }
        val str = value.toString()
        for (fmt in DATE_FORMATS) {
            try { return LocalDate.parse(str, fmt) } catch (_: DateTimeParseException) {}
        }
        return null
    }
}

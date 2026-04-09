package dev.jarviis.obsidian.parser

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Obsidian template variable substitution.
 *
 * Supported variables:
 *   {{date}}              — current date in default format (YYYY-MM-DD)
 *   {{date:YYYY-MM-DD}}   — date with explicit format
 *   {{time}}              — current time (HH:mm)
 *   {{time:HH:mm:ss}}     — time with explicit format
 *   {{title}}             — note title (filename without extension)
 */
object TemplateEngine {

    private val DATE_VAR = Regex("""{{date(?::([^}]+))?}}""")
    private val TIME_VAR = Regex("""{{time(?::([^}]+))?}}""")
    private val TITLE_VAR = Regex("""{{title}}""")

    private val DEFAULT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DEFAULT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    fun render(
        template: String,
        title: String,
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now(),
    ): String {
        var result = template

        result = DATE_VAR.replace(result) { match ->
            val pattern = match.groupValues[1].takeIf { it.isNotEmpty() }
                ?.toObsidianDatePattern()
            val fmt = pattern?.let { DateTimeFormatter.ofPattern(it) } ?: DEFAULT_DATE_FORMAT
            date.format(fmt)
        }

        result = TIME_VAR.replace(result) { match ->
            val pattern = match.groupValues[1].takeIf { it.isNotEmpty() }
                ?.toObsidianDatePattern()
            val fmt = pattern?.let { DateTimeFormatter.ofPattern(it) } ?: DEFAULT_TIME_FORMAT
            time.format(fmt)
        }

        result = TITLE_VAR.replace(result) { title }

        return result
    }

    /** Convert Obsidian moment.js-style tokens to java.time pattern tokens. */
    private fun String.toObsidianDatePattern(): String =
        this
            .replace("YYYY", "yyyy")
            .replace("DD", "dd")
            .replace("Do", "d")  // day-of-month ordinal — approximate
            .replace("ddd", "EEE")
            .replace("dddd", "EEEE")
            .replace("A", "a")
            .replace("HH", "HH")
            .replace("hh", "hh")
            .replace("mm", "mm")
            .replace("ss", "ss")
}

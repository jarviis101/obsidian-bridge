package dev.jarviis.obsidian.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FrontmatterParserTest {

    @Test fun `no frontmatter returns empty`() {
        val fm = FrontmatterParser.parse("# Just a heading\nSome content.")
        assertEquals(emptyList<String>(), fm.tags)
        assertEquals(emptyList<String>(), fm.aliases)
        assertNull(fm.date)
    }

    @Test fun `parses tags as list`() {
        val text = "---\ntags: [kotlin, ide]\n---\nBody."
        val fm = FrontmatterParser.parse(text)
        assertEquals(listOf("kotlin", "ide"), fm.tags)
    }

    @Test fun `parses tags with hash prefix (quoted)`() {
        // In YAML, '#' starts a comment when unquoted; users who quote their tags work correctly.
        val text = "---\ntags: ['#kotlin', '#ide']\n---\nBody."
        val fm = FrontmatterParser.parse(text)
        assertEquals(listOf("kotlin", "ide"), fm.tags)
    }

    @Test fun `unquoted hash tags in yaml are treated as comments`() {
        // [#kotlin, #ide] — YAML sees '#kotlin' as a comment start → empty list
        val text = "---\ntags: []\n---\nBody."
        val fm = FrontmatterParser.parse(text)
        assertEquals(emptyList<String>(), fm.tags)
    }

    @Test fun `parses aliases`() {
        val text = "---\naliases: [My Note, The Note]\n---\nBody."
        val fm = FrontmatterParser.parse(text)
        assertEquals(listOf("My Note", "The Note"), fm.aliases)
    }

    @Test fun `parses date`() {
        val text = "---\ndate: 2024-03-15\n---\nBody."
        val fm = FrontmatterParser.parse(text)
        assertEquals(LocalDate.of(2024, 3, 15), fm.date)
    }

    @Test fun `malformed yaml returns empty`() {
        val text = "---\n: broken: yaml: here\n---\nBody."
        val fm = FrontmatterParser.parse(text)  // must not throw
        assertTrue(fm.tags.isEmpty())
    }

    @Test fun `extracts raw yaml block`() {
        val text = "---\ntitle: Test\n---\nBody."
        assertEquals("title: Test", FrontmatterParser.extractRaw(text))
    }
}

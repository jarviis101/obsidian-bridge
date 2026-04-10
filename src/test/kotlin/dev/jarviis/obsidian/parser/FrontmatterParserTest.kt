package dev.jarviis.obsidian.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontmatterParserTest {

    @Test fun `no frontmatter returns empty`() {
        val fm = FrontmatterParser.parse("# Just a heading\nSome content.")
        assertEquals(emptyList<String>(), fm.aliases)
    }

    @Test fun `parses aliases`() {
        val text = "---\naliases: [My Note, The Note]\n---\nBody."
        val fm = FrontmatterParser.parse(text)
        assertEquals(listOf("My Note", "The Note"), fm.aliases)
    }

    @Test fun `alias as single string`() {
        val text = "---\naliases: My Note\n---\nBody."
        val fm = FrontmatterParser.parse(text)
        assertEquals(listOf("My Note"), fm.aliases)
    }

    @Test fun `malformed yaml returns empty`() {
        val text = "---\n: broken: yaml: here\n---\nBody."
        val fm = FrontmatterParser.parse(text)
        assertTrue(fm.aliases.isEmpty())
    }

    @Test fun `extracts raw yaml block`() {
        val text = "---\ntitle: Test\n---\nBody."
        assertEquals("title: Test", FrontmatterParser.extractRaw(text))
    }

    @Test fun `no frontmatter extractRaw returns null`() {
        assertEquals(null, FrontmatterParser.extractRaw("# No frontmatter"))
    }
}

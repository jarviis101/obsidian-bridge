package dev.jarviis.obsidian.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiLinkParserTest {

    @Test fun `plain link`() {
        val links = WikiLinkParser.parse("See [[My Note]] for details.")
        assertEquals(1, links.size)
        assertEquals("My Note", links[0].target)
        assertFalse(links[0].isEmbed)
    }

    @Test fun `link with alias - target only`() {
        val links = WikiLinkParser.parse("[[Note Name|click here]]")
        assertEquals(1, links.size)
        assertEquals("Note Name", links[0].target)
    }

    @Test fun `link with heading - target only`() {
        val links = WikiLinkParser.parse("[[Note#Introduction]]")
        assertEquals(1, links.size)
        assertEquals("Note", links[0].target)
    }

    @Test fun `link with block id - target only`() {
        val links = WikiLinkParser.parse("[[Note#^abc123]]")
        assertEquals(1, links.size)
        assertEquals("Note", links[0].target)
    }

    @Test fun `embed link`() {
        val links = WikiLinkParser.parse("![[image.png]]")
        assertEquals(1, links.size)
        assertTrue(links[0].isEmbed)
        assertEquals("image.png", links[0].target)
    }

    @Test fun `multiple links in text`() {
        val links = WikiLinkParser.parse("See [[A]] and [[B|alias]] and ![[C]].")
        assertEquals(3, links.size)
    }

    @Test fun `link with heading and alias - target only`() {
        val links = WikiLinkParser.parse("[[Note#Section|See here]]")
        assertEquals(1, links.size)
        assertEquals("Note", links[0].target)
    }

    @Test fun `start offset is correct`() {
        val text = "Text [[Note]] end"
        val links = WikiLinkParser.parse(text)
        assertEquals(1, links.size)
        assertEquals(5, links[0].startOffset)
    }

    @Test fun `no links returns empty list`() {
        assertTrue(WikiLinkParser.parse("No links here.").isEmpty())
    }

    @Test fun `escaped pipe in table cell`() {
        val links = WikiLinkParser.parse("| [[Note\\|Alias]] |")
        assertEquals(1, links.size)
        assertEquals("Note", links[0].target)
    }
}

package dev.jarviis.obsidian.parser

import dev.jarviis.obsidian.model.WikiLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiLinkParserTest {

    @Test fun `plain link`() {
        val links = WikiLinkParser.parse("See [[My Note]] for details.")
        assertEquals(1, links.size)
        assertEquals("My Note", links[0].target)
        assertNull(links[0].alias)
        assertNull(links[0].heading)
        assertFalse(links[0].isEmbed)
    }

    @Test fun `link with alias`() {
        val links = WikiLinkParser.parse("[[Note Name|click here]]")
        assertEquals("Note Name", links[0].target)
        assertEquals("click here", links[0].alias)
    }

    @Test fun `link with heading`() {
        val links = WikiLinkParser.parse("[[Note#Introduction]]")
        assertEquals("Note", links[0].target)
        assertEquals("Introduction", links[0].heading)
        assertNull(links[0].blockId)
    }

    @Test fun `link with block id`() {
        val links = WikiLinkParser.parse("[[Note#^abc123]]")
        assertEquals("Note", links[0].target)
        assertNull(links[0].heading)
        assertEquals("abc123", links[0].blockId)
    }

    @Test fun `embed link`() {
        val links = WikiLinkParser.parse("![[image.png]]")
        assertTrue(links[0].isEmbed)
        assertEquals("image.png", links[0].target)
    }

    @Test fun `multiple links in text`() {
        val links = WikiLinkParser.parse("See [[A]] and [[B|alias]] and ![[C]].")
        assertEquals(3, links.size)
    }

    @Test fun `link with heading and alias`() {
        val links = WikiLinkParser.parse("[[Note#Section|See here]]")
        assertEquals("Note", links[0].target)
        assertEquals("Section", links[0].heading)
        assertEquals("See here", links[0].alias)
    }

    @Test fun `offsets are correct`() {
        val text = "Text [[Note]] end"
        val links = WikiLinkParser.parse(text)
        assertEquals(5, links[0].startOffset)
        assertEquals(13, links[0].endOffset)
        assertEquals("[[Note]]", text.substring(links[0].startOffset, links[0].endOffset))
    }

    @Test fun `findAt returns correct link`() {
        val text = "[[Alpha]] and [[Beta]]"
        val link = WikiLinkParser.findAt(text, 17)  // inside [[Beta]]
        assertEquals("Beta", link?.target)
    }

    @Test fun `no links returns empty list`() {
        assertTrue(WikiLinkParser.parse("No links here.").isEmpty())
    }
}

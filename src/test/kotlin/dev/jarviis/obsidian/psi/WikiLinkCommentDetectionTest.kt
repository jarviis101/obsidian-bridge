package dev.jarviis.obsidian.psi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiLinkCommentDetectionTest {

    // ── isInCommentByLinePrefix ───────────────────────────────────────────────

    private fun detect(line: String): Boolean {
        val openBracket = line.indexOf("[[")
        return isInCommentByLinePrefix(line, openBracket)
    }

    @Test fun `single-line comment Java-style`() = assertTrue(detect("    // TODO: [[Note]]"))
    @Test fun `single-line comment no space`() = assertTrue(detect("//[[Note]]"))
    @Test fun `hash comment Python-style`() = assertTrue(detect("# [[Note]]"))
    @Test fun `block comment continuation line`() = assertTrue(detect(" * See [[Note]] for details"))
    @Test fun `block comment opening`() = assertTrue(detect("/* [[Note]]"))
    @Test fun `not a comment - plain code`() = assertFalse(detect("val x = [[Note]]"))
    @Test fun `not a comment - string literal`() = assertFalse(detect("    echo \"[[Note]]\""))

    @Test fun `multiline - second line is comment`() {
        val text = "val x = 1\n// [[Note]]"
        assertTrue(isInCommentByLinePrefix(text, text.indexOf("[[")))
    }

    @Test fun `multiline - first line comment does not bleed into second`() {
        val text = "// comment\nval x = [[Note]]"
        assertFalse(isInCommentByLinePrefix(text, text.indexOf("[[")))
    }

    @Test fun `indented comment`() = assertTrue(detect("        // [[Note]]"))

    // ── findWikiLinkAtCaret ───────────────────────────────────────────────────

    private fun atCaret(text: String, cursorMarker: String = "|"): WikiLinkAtCaret? {
        val offset = text.indexOf(cursorMarker)
        val clean = text.removeRange(offset, offset + cursorMarker.length)
        return findWikiLinkAtCaret(clean, offset)
    }

    @Test fun `returns prefix when caret is inside open link`() {
        val result = atCaret("[[My Not|")
        assertNotNull(result)
        assertEquals("My Not", result!!.prefix)
    }

    @Test fun `returns empty prefix immediately after opening brackets`() {
        val result = atCaret("[[|")
        assertNotNull(result)
        assertEquals("", result!!.prefix)
    }

    @Test fun `strips heading from prefix`() {
        val result = atCaret("[[Note#Section|")
        assertNotNull(result)
        assertEquals("Note", result!!.prefix)
    }

    @Test fun `strips alias separator from prefix`() {
        val result = atCaret("[[Note|Alias|")
        assertNotNull(result)
        assertEquals("Note", result!!.prefix)
    }

    @Test fun `returns null when link is already closed`() {
        assertNull(atCaret("[[Note]] |"))
    }

    @Test fun `returns null when no opening bracket`() {
        assertNull(atCaret("just text |"))
    }

    @Test fun `returns null when offset is zero`() {
        assertNull(findWikiLinkAtCaret("[[Note]]", 0))
    }

    @Test fun `returns null when caret is before end of opening brackets`() {
        // offset points at the second '[', not past it
        val text = "[[Note]]"
        assertNull(findWikiLinkAtCaret(text, 1))
    }

    @Test fun `openBracket position is correct`() {
        val text = "prefix [[Note"
        val offset = text.length
        val result = findWikiLinkAtCaret(text, offset)
        assertNotNull(result)
        assertEquals(7, result!!.openBracket)
    }
}

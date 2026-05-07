package com.pureframe.exif.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameGeneratorTest {

    @Test
    fun generate_includesPrefixAndTimestampAndUuid() {
        val result = FilenameGenerator.generate("jpg")
        assertTrue("Expected prefix EXIFPure_, got $result", result.startsWith("EXIFPure_"))
        // Format: EXIFPure_yyyyMMdd_HHmmss_xxxxxxxx.jpg
        val parts = result.removePrefix("EXIFPure_").split("_")
        assertEquals("Should have 3 underscore-separated parts after prefix", 3, parts.size)
        assertEquals("Timestamp part should be 8 chars (yyyyMMdd)", 8, parts[0].length)
        assertEquals("Time part should be 6 chars (HHmmss)", 6, parts[1].length)
        assertEquals("UUID part + extension should be 8+1+3=12 chars", 12, parts[2].length)
    }

    @Test
    fun generate_stripsLeadingDotFromExtension() {
        val withDot = FilenameGenerator.generate(".png")
        val withoutDot = FilenameGenerator.generate("png")
        assertTrue("Should end with .png", withDot.endsWith(".png"))
        assertTrue("Should end with .png", withoutDot.endsWith(".png"))
    }

    @Test
    fun generate_lowercasesExtension() {
        val result = FilenameGenerator.generate("JPEG")
        assertTrue("Should lowercase extension", result.endsWith(".jpeg"))
    }

    @Test
    fun generate_producesUniqueNames() {
        val names = (1..100).map { FilenameGenerator.generate("jpg") }
        val distinct = names.toSet()
        assertEquals("100 generated names should all be distinct", 100, distinct.size)
    }
}

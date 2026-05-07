package com.pureframe.exif.data.local.stripper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class JpegStripperTest {

    // ── Helper builders ──────────────────────────────────────────────────────

    private fun marker(m: Int) = byteArrayOf(0xFF.toByte(), m.toByte())

    private fun segment(markerByte: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(
            0xFF.toByte(),
            markerByte.toByte(),
            (length shr 8).toByte(),
            (length and 0xFF).toByte(),
            *payload
        )
    }

    private fun strip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        JpegStripper.strip(ByteArrayInputStream(input), out)
        return out.toByteArray()
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun strip_throwsWhenMissingSoi() {
        val invalid = byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xD9.toByte())
        strip(invalid)
    }

    @Test
    fun strip_passesThroughMinimalJpegWithNoMetadata() {
        val original = buildMinimalJpeg()
        val cleaned = strip(original)
        assertArrayEquals("JPEG with no metadata should be unchanged", original, cleaned)
    }

    @Test
    fun strip_removesApp1ExifSegment() {
        val app1Payload = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(20) { 0x42 }
        val app1 = segment(0xE1, app1Payload)

        val original = buildMinimalJpeg(insertAfterSoi = app1)
        val cleaned = strip(original)

        assertFalse("APP1 segment should be removed", containsMarker(cleaned, 0xE1))
        assertJpegStructureValid(cleaned)
    }

    @Test
    fun strip_removesApp13IptcSegment() {
        val app13Payload = ByteArray(30) { 0xAB.toByte() }
        val app13 = segment(0xED, app13Payload)

        val original = buildMinimalJpeg(insertAfterSoi = app13)
        val cleaned = strip(original)

        assertFalse("APP13 segment should be removed", containsMarker(cleaned, 0xED))
        assertJpegStructureValid(cleaned)
    }

    @Test
    fun strip_removesComSegment() {
        val comPayload = "This is a comment".toByteArray(Charsets.US_ASCII)
        val com = segment(0xFE, comPayload)

        val original = buildMinimalJpeg(insertAfterSoi = com)
        val cleaned = strip(original)

        assertFalse("COM segment should be removed", containsMarker(cleaned, 0xFE))
        assertJpegStructureValid(cleaned)
    }

    @Test
    fun strip_preservesApp0JfiF() {
        val app0Payload = "JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val app0 = segment(0xE0, app0Payload)

        val original = buildMinimalJpeg(insertAfterSoi = app0)
        val cleaned = strip(original)

        assertJpegStructureValid(cleaned)
        // APP0 should survive because it's not in the strip list (0xE1, 0xED, 0xFE)
        assertEquals("APP0 should be preserved", true, containsMarker(cleaned, 0xE0))
    }

    @Test
    fun strip_preservesEntropyWithEscapedFF() {
        // Entropy data containing the literal byte 0xFF followed by 0x00 (escape)
        val entropy = byteArrayOf(0x01, 0x02, 0xFF.toByte(), 0x00, 0x03, 0x04)
        val original = buildMinimalJpeg(entropyData = entropy)
        val cleaned = strip(original)

        assertJpegStructureValid(cleaned)
        // Find the entropy region (after SOS segment) and verify FF00 is intact
        val sosIndex = indexOfMarker(cleaned, 0xDA)
        assertEquals("SOS marker should be present", true, sosIndex >= 0)

        // Read the SOS segment length to know where entropy data begins
        val lengthHigh = cleaned[sosIndex + 2].toInt() and 0xFF
        val lengthLow = cleaned[sosIndex + 3].toInt() and 0xFF
        val sosLength = (lengthHigh shl 8) or lengthLow
        val entropyStart = sosIndex + 2 + sosLength // marker bytes + entire segment
        val entropySlice = cleaned.copyOfRange(entropyStart, cleaned.size - 2)
        assertArrayEquals("Escaped 0xFF00 in entropy should be preserved", entropy, entropySlice)
    }

    @Test
    fun strip_preservesDqtDhtSofSegments() {
        val dqtPayload = ByteArray(64) { it.toByte() }
        val dqt = segment(0xDB, dqtPayload)

        val sofPayload = byteArrayOf(0x08, 0x00, 0x10, 0x00, 0x10, 0x01, 0x01, 0x11, 0x00)
        val sof = segment(0xC0, sofPayload)

        val original = buildMinimalJpeg(insertAfterSoi = dqt + sof)
        val cleaned = strip(original)

        assertJpegStructureValid(cleaned)
        assertEquals("DQT should be preserved", true, containsMarker(cleaned, 0xDB))
        assertEquals("SOF0 should be preserved", true, containsMarker(cleaned, 0xC0))
    }

    @Test
    fun strip_multipleMetadataSegments_allRemoved() {
        val app1 = segment(0xE1, ByteArray(10) { 0x11.toByte() })
        val app13 = segment(0xED, ByteArray(10) { 0x22.toByte() })
        val com = segment(0xFE, ByteArray(10) { 0x33.toByte() })
        val app0 = segment(0xE0, ByteArray(10) { 0x44.toByte() })

        val original = buildMinimalJpeg(insertAfterSoi = app1 + app0 + app13 + com)
        val cleaned = strip(original)

        assertJpegStructureValid(cleaned)
        assertFalse("APP1 removed", containsMarker(cleaned, 0xE1))
        assertFalse("APP13 removed", containsMarker(cleaned, 0xED))
        assertFalse("COM removed", containsMarker(cleaned, 0xFE))
        assertEquals("APP0 preserved", true, containsMarker(cleaned, 0xE0))
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Builds a minimal synthetically valid JPEG:
     * SOI → [optional inserted segments] → SOS → [entropy] → EOI
     */
    private fun buildMinimalJpeg(
        insertAfterSoi: ByteArray = byteArrayOf(),
        entropyData: ByteArray = byteArrayOf(0x00, 0x01, 0x02, 0x03)
    ): ByteArray {
        val sosPayload = byteArrayOf(0x03, 0x01, 0x01, 0x00) // minimal SOS header
        val sos = segment(0xDA, sosPayload)
        return marker(0xD8) + insertAfterSoi + sos + entropyData + marker(0xD9)
    }

    private fun containsMarker(data: ByteArray, markerByte: Int): Boolean {
        var i = 0
        while (i < data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == markerByte.toByte()) return true
            i++
        }
        return false
    }

    private fun indexOfMarker(data: ByteArray, markerByte: Int): Int {
        var i = 0
        while (i < data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == markerByte.toByte()) return i
            i++
        }
        return -1
    }

    private fun assertJpegStructureValid(data: ByteArray) {
        assertEquals("Must start with SOI", 0xD8, data[1].toInt() and 0xFF)
        assertEquals("Must end with EOI", 0xD9, data[data.size - 1].toInt() and 0xFF)
        assertEquals("EOI must be preceded by 0xFF", 0xFF, data[data.size - 2].toInt() and 0xFF)
    }
}

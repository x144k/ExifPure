package com.pureframe.exif.data.local.stripper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class JpegGpsStripperTest {

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
        JpegGpsStripper.strip(ByteArrayInputStream(input), out)
        return out.toByteArray()
    }

    private fun int16LE(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte()
    )

    private fun int32LE(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte()
    )

    private fun int16BE(v: Int) = byteArrayOf(
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte()
    )

    private fun int32BE(v: Int) = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte()
    )

    /** Builds an APP1 segment with a minimal little-endian EXIF containing a GPS IFD. */
    private fun buildExifApp1(): ByteArray {
        val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val tiffHeader = "II".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        val ifd0 = int16LE(1) +
                int16LE(0x8825) + int16LE(4) + int32LE(1) + int32LE(26) +
                int32LE(0)
        val gpsIfd = int16LE(1) +
                int16LE(0x0000) + int16LE(1) + int32LE(1) + int32LE(0) +
                int32LE(0)
        return segment(0xE1, exifHeader + tiffHeader + ifd0 + gpsIfd)
    }

    private fun buildMinimalJpeg(insertAfterSoi: ByteArray = byteArrayOf()): ByteArray {
        val sosPayload = byteArrayOf(0x03, 0x01, 0x01, 0x00)
        val sos = segment(0xDA, sosPayload)
        val entropy = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        return marker(0xD8) + insertAfterSoi + sos + entropy + marker(0xD9)
    }

    @Test
    fun strip_gpsRemoved() {
        val original = buildMinimalJpeg(insertAfterSoi = buildExifApp1())
        val cleaned = strip(original)

        assertJpegStructureValid(cleaned)

        // GPS IFD entry count (at payload offset 32) should be zeroed
        val gpsEntryCountLow = cleaned[38].toInt() and 0xFF
        val gpsEntryCountHigh = cleaned[39].toInt() and 0xFF
        assertEquals("GPS IFD count low byte zeroed", 0x00, gpsEntryCountLow)
        assertEquals("GPS IFD count high byte zeroed", 0x00, gpsEntryCountHigh)
    }

    @Test
    fun strip_gpsRemoved_bigEndian() {
        val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val tiffHeader = "MM".toByteArray(Charsets.US_ASCII) + int16BE(0x002A) + int32BE(8)
        val ifd0 = int16BE(1) +
                int16BE(0x8825) + int16BE(4) + int32BE(1) + int32BE(26) +
                int32BE(0)
        val gpsIfd = int16BE(1) +
                int16BE(0x0000) + int16BE(1) + int32BE(1) + int32BE(0) +
                int32BE(0)
        val app1 = segment(0xE1, exifHeader + tiffHeader + ifd0 + gpsIfd)

        val original = buildMinimalJpeg(insertAfterSoi = app1)
        val cleaned = strip(original)

        assertJpegStructureValid(cleaned)

        val gpsEntryCountHigh = cleaned[38].toInt() and 0xFF
        val gpsEntryCountLow = cleaned[39].toInt() and 0xFF
        assertEquals("GPS IFD count high byte zeroed", 0x00, gpsEntryCountHigh)
        assertEquals("GPS IFD count low byte zeroed", 0x00, gpsEntryCountLow)
    }

    @Test
    fun strip_noGps_unchanged() {
        // EXIF without a GPS pointer entry
        val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val tiffHeader = "II".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        val ifd0 = int16LE(1) +
                int16LE(0x010F) + int16LE(2) + int32LE(1) + int32LE(0) +
                int32LE(0)
        val app1 = segment(0xE1, exifHeader + tiffHeader + ifd0)

        val original = buildMinimalJpeg(insertAfterSoi = app1)
        val cleaned = strip(original)

        assertArrayEquals("JPEG without GPS should be unchanged", original, cleaned)
    }

    @Test(expected = IllegalStateException::class)
    fun strip_eofDuringRead_throws() {
        // Truncated APP1 segment
        val truncated = marker(0xD8) + byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0x00) + marker(0xD9)
        strip(truncated)
    }

    @Test
    fun strip_noApp1_unchanged() {
        val original = buildMinimalJpeg()
        val cleaned = strip(original)
        assertArrayEquals("JPEG without APP1 should be unchanged", original, cleaned)
    }

    @Test
    fun strip_app1NotExif_unchanged() {
        val app1 = segment(0xE1, "XMP\u0000".toByteArray(Charsets.US_ASCII))
        val original = buildMinimalJpeg(insertAfterSoi = app1)
        val cleaned = strip(original)
        assertJpegStructureValid(cleaned)
        assertArrayEquals("APP1 that is not EXIF should be unchanged", original, cleaned)
    }

    @Test
    fun strip_corruptedTiffEndian_unchanged() {
        val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val badTiff = "XX".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        val app1 = segment(0xE1, exifHeader + badTiff)
        val original = buildMinimalJpeg(insertAfterSoi = app1)
        val cleaned = strip(original)
        assertJpegStructureValid(cleaned)
        assertArrayEquals("Corrupted TIFF endian should leave APP1 unchanged", original, cleaned)
    }

    @Test
    fun strip_gpsPointerOverlapsIfd0_unchanged() {
        val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val tiffHeader = "II".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        // GPS pointer (8) overlaps IFD0 start (8), triggering the overlap guard
        val ifd0 = int16LE(1) +
                int16LE(0x8825) + int16LE(4) + int32LE(1) + int32LE(8) +
                int32LE(0)
        val app1 = segment(0xE1, exifHeader + tiffHeader + ifd0)
        val original = buildMinimalJpeg(insertAfterSoi = app1)
        val cleaned = strip(original)
        assertJpegStructureValid(cleaned)
        assertArrayEquals("Overlapping GPS pointer should leave APP1 unchanged", original, cleaned)
    }

    @Test
    fun strip_gpsWrongType_unchanged() {
        val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val tiffHeader = "II".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        // GPS pointer with type = 1 (should be 4) and count = 1
        val ifd0 = int16LE(1) +
                int16LE(0x8825) + int16LE(1) + int32LE(1) + int32LE(26) +
                int32LE(0)
        val app1 = segment(0xE1, exifHeader + tiffHeader + ifd0)
        val original = buildMinimalJpeg(insertAfterSoi = app1)
        val cleaned = strip(original)
        assertJpegStructureValid(cleaned)
        assertArrayEquals("GPS pointer with wrong type should leave APP1 unchanged", original, cleaned)
    }

    @Test
    fun strip_gpsPointerOutOfBounds_unchanged() {
        val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val tiffHeader = "II".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        // GPS pointer points well past the end of the EXIF data
        val ifd0 = int16LE(1) +
                int16LE(0x8825) + int16LE(4) + int32LE(1) + int32LE(1000) +
                int32LE(0)
        val app1 = segment(0xE1, exifHeader + tiffHeader + ifd0)
        val original = buildMinimalJpeg(insertAfterSoi = app1)
        val cleaned = strip(original)
        assertJpegStructureValid(cleaned)
        assertArrayEquals("Out-of-bounds GPS pointer should leave APP1 unchanged", original, cleaned)
    }

    private fun assertJpegStructureValid(data: ByteArray) {
        assertEquals("Must start with SOI", 0xD8, data[1].toInt() and 0xFF)
        assertEquals("Must end with EOI", 0xD9, data[data.size - 1].toInt() and 0xFF)
        assertEquals("EOI must be preceded by 0xFF", 0xFF, data[data.size - 2].toInt() and 0xFF)
    }
}

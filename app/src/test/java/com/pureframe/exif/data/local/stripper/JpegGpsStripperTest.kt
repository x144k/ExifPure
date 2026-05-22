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

        // Verify still a valid JPEG
        assertEquals("Must start with SOI", 0xD8, cleaned[1].toInt() and 0xFF)
        assertEquals("Must end with EOI", 0xD9, cleaned[cleaned.size - 1].toInt() and 0xFF)

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

        assertEquals("Must start with SOI", 0xD8, cleaned[1].toInt() and 0xFF)
        assertEquals("Must end with EOI", 0xD9, cleaned[cleaned.size - 1].toInt() and 0xFF)

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
}

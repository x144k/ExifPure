package com.pureframe.exif.data.local.stripper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

class PngGpsStripperTest {

    private fun chunkType(name: String): Int =
        (name[0].code shl 24) or (name[1].code shl 16) or (name[2].code shl 8) or name[3].code

    private fun computeCrc(type: String, data: ByteArray): Int {
        val crc = CRC32()
        crc.update(type.toByteArray(Charsets.US_ASCII))
        crc.update(data)
        return crc.value.toInt()
    }

    private fun ByteArrayOutputStream.writeInt(v: Int) {
        write(v shr 24 and 0xFF)
        write(v shr 16 and 0xFF)
        write(v shr 8 and 0xFF)
        write(v and 0xFF)
    }

    private fun writeChunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = computeCrc(type, data)
        out.writeInt(data.size)
        out.write(typeBytes)
        out.write(data)
        out.writeInt(crc)
        return out.toByteArray()
    }

    private fun buildMinimalPng(insertBeforeIdat: ByteArray = byteArrayOf()): ByteArray {
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        val ihdrData = byteArrayOf(
            0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x01,
            0x08, 0x00, 0x00, 0x00, 0x00
        )
        val ihdr = writeChunk("IHDR", ihdrData)

        val rawData = byteArrayOf(0x00, 0x00)
        val deflater = Deflater()
        deflater.setInput(rawData)
        deflater.finish()
        val compressed = ByteArray(32)
        val compressedLen = deflater.deflate(compressed)
        deflater.end()
        val idat = writeChunk("IDAT", compressed.copyOf(compressedLen))

        val iend = writeChunk("IEND", byteArrayOf())

        return signature + ihdr + insertBeforeIdat + idat + iend
    }

    private fun strip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        PngGpsStripper.strip(ByteArrayInputStream(input), out)
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

    /** Builds a minimal eXIf chunk data with GPS IFD. */
    private fun buildExifData(): ByteArray {
        val tiffHeader = "II".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        val ifd0 = int16LE(1) +
                int16LE(0x8825) + int16LE(4) + int32LE(1) + int32LE(26) +
                int32LE(0)
        val gpsIfd = int16LE(1) +
                int16LE(0x0000) + int16LE(1) + int32LE(1) + int32LE(0) +
                int32LE(0)
        return tiffHeader + ifd0 + gpsIfd
    }

    @Test
    fun strip_gpsRemoved() {
        val exifChunk = writeChunk("eXIf", buildExifData())
        val original = buildMinimalPng(insertBeforeIdat = exifChunk)
        val cleaned = strip(original)

        assertPngStructureValid(cleaned)

        // GPS IFD entry count at offset 26 in eXIf data should be zeroed
        var i = 8
        while (i < cleaned.size - 8) {
            val length = ((cleaned[i].toInt() and 0xFF) shl 24) or
                    ((cleaned[i + 1].toInt() and 0xFF) shl 16) or
                    ((cleaned[i + 2].toInt() and 0xFF) shl 8) or
                    (cleaned[i + 3].toInt() and 0xFF)
            val type = ((cleaned[i + 4].toInt() and 0xFF) shl 24) or
                    ((cleaned[i + 5].toInt() and 0xFF) shl 16) or
                    ((cleaned[i + 6].toInt() and 0xFF) shl 8) or
                    (cleaned[i + 7].toInt() and 0xFF)
            if (type == chunkType("eXIf")) {
                val gpsIfdOffset = i + 8 + 26
                assertEquals("GPS IFD count low byte zeroed", 0x00, cleaned[gpsIfdOffset].toInt() and 0xFF)
                assertEquals("GPS IFD count high byte zeroed", 0x00, cleaned[gpsIfdOffset + 1].toInt() and 0xFF)

                // Verify the chunk CRC was recalculated correctly
                val chunkData = cleaned.copyOfRange(i + 8, i + 8 + length)
                val typeBytes = byteArrayOf(
                    (type shr 24).toByte(), (type shr 16).toByte(),
                    (type shr 8).toByte(), type.toByte()
                )
                val expectedCrc = CRC32().apply { update(typeBytes); update(chunkData) }.value.toInt()
                val actualCrc = ((cleaned[i + 8 + length].toInt() and 0xFF) shl 24) or
                        ((cleaned[i + 9 + length].toInt() and 0xFF) shl 16) or
                        ((cleaned[i + 10 + length].toInt() and 0xFF) shl 8) or
                        (cleaned[i + 11 + length].toInt() and 0xFF)
                assertEquals("eXIf chunk CRC should be recalculated", expectedCrc, actualCrc)
                return
            }
            i += 12 + length
        }
        throw AssertionError("eXIf chunk not found")
    }

    @Test
    fun strip_gpsRemoved_bigEndian() {
        val tiffHeader = "MM".toByteArray(Charsets.US_ASCII) + int16BE(0x002A) + int32BE(8)
        val ifd0 = int16BE(1) +
                int16BE(0x8825) + int16BE(4) + int32BE(1) + int32BE(26) +
                int32BE(0)
        val gpsIfd = int16BE(1) +
                int16BE(0x0000) + int16BE(1) + int32BE(1) + int32BE(0) +
                int32BE(0)
        val exifChunk = writeChunk("eXIf", tiffHeader + ifd0 + gpsIfd)

        val original = buildMinimalPng(insertBeforeIdat = exifChunk)
        val cleaned = strip(original)

        assertPngStructureValid(cleaned)

        var i = 8
        while (i < cleaned.size - 8) {
            val length = ((cleaned[i].toInt() and 0xFF) shl 24) or
                    ((cleaned[i + 1].toInt() and 0xFF) shl 16) or
                    ((cleaned[i + 2].toInt() and 0xFF) shl 8) or
                    (cleaned[i + 3].toInt() and 0xFF)
            val type = ((cleaned[i + 4].toInt() and 0xFF) shl 24) or
                    ((cleaned[i + 5].toInt() and 0xFF) shl 16) or
                    ((cleaned[i + 6].toInt() and 0xFF) shl 8) or
                    (cleaned[i + 7].toInt() and 0xFF)
            if (type == chunkType("eXIf")) {
                val gpsIfdOffset = i + 8 + 26
                assertEquals("GPS IFD count high byte zeroed", 0x00, cleaned[gpsIfdOffset].toInt() and 0xFF)
                assertEquals("GPS IFD count low byte zeroed", 0x00, cleaned[gpsIfdOffset + 1].toInt() and 0xFF)

                // Verify the chunk CRC was recalculated correctly
                val chunkData = cleaned.copyOfRange(i + 8, i + 8 + length)
                val typeBytes = byteArrayOf(
                    (type shr 24).toByte(), (type shr 16).toByte(),
                    (type shr 8).toByte(), type.toByte()
                )
                val expectedCrc = CRC32().apply { update(typeBytes); update(chunkData) }.value.toInt()
                val actualCrc = ((cleaned[i + 8 + length].toInt() and 0xFF) shl 24) or
                        ((cleaned[i + 9 + length].toInt() and 0xFF) shl 16) or
                        ((cleaned[i + 10 + length].toInt() and 0xFF) shl 8) or
                        (cleaned[i + 11 + length].toInt() and 0xFF)
                assertEquals("eXIf chunk CRC should be recalculated", expectedCrc, actualCrc)
                return
            }
            i += 12 + length
        }
        throw AssertionError("eXIf chunk not found")
    }

    @Test
    fun strip_noExif_unchanged() {
        val original = buildMinimalPng()
        val cleaned = strip(original)
        assertArrayEquals("PNG without eXIf should be unchanged", original, cleaned)
    }

    @Test(expected = IllegalArgumentException::class)
    fun strip_oversizedChunk_throws() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        out.writeInt(0x06400001) // 100 MB + 1
        out.write("eXIf".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x00))
        strip(out.toByteArray())
    }

    @Test
    fun strip_corruptedTiffEndian_unchanged() {
        val badTiff = "XX".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        val exifChunk = writeChunk("eXIf", badTiff)
        val original = buildMinimalPng(insertBeforeIdat = exifChunk)
        val cleaned = strip(original)
        assertPngStructureValid(cleaned)
        assertArrayEquals("Corrupted TIFF endian should leave eXIf unchanged", original, cleaned)
    }

    @Test
    fun strip_gpsPointerOverlapsIfd0_unchanged() {
        val tiffHeader = "II".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        // GPS pointer (8) overlaps IFD0 start (8), triggering the overlap guard
        val ifd0 = int16LE(1) +
                int16LE(0x8825) + int16LE(4) + int32LE(1) + int32LE(8) +
                int32LE(0)
        val exifChunk = writeChunk("eXIf", tiffHeader + ifd0)
        val original = buildMinimalPng(insertBeforeIdat = exifChunk)
        val cleaned = strip(original)
        assertPngStructureValid(cleaned)
        assertArrayEquals("Overlapping GPS pointer should leave eXIf unchanged", original, cleaned)
    }

    @Test
    fun strip_gpsWrongType_unchanged() {
        val tiffHeader = "II".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        // GPS pointer with type = 1 (should be 4) and count = 1
        val ifd0 = int16LE(1) +
                int16LE(0x8825) + int16LE(1) + int32LE(1) + int32LE(26) +
                int32LE(0)
        val exifChunk = writeChunk("eXIf", tiffHeader + ifd0)
        val original = buildMinimalPng(insertBeforeIdat = exifChunk)
        val cleaned = strip(original)
        assertPngStructureValid(cleaned)
        assertArrayEquals("GPS pointer with wrong type should leave eXIf unchanged", original, cleaned)
    }

    @Test
    fun strip_gpsPointerOutOfBounds_unchanged() {
        val tiffHeader = "II".toByteArray(Charsets.US_ASCII) + int16LE(0x002A) + int32LE(8)
        // GPS pointer points well past the end of the eXIf data
        val ifd0 = int16LE(1) +
                int16LE(0x8825) + int16LE(4) + int32LE(1) + int32LE(1000) +
                int32LE(0)
        val exifChunk = writeChunk("eXIf", tiffHeader + ifd0)
        val original = buildMinimalPng(insertBeforeIdat = exifChunk)
        val cleaned = strip(original)
        assertPngStructureValid(cleaned)
        assertArrayEquals("Out-of-bounds GPS pointer should leave eXIf unchanged", original, cleaned)
    }

    @Test(expected = IllegalStateException::class)
    fun strip_truncatedChunk_throws() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        out.writeInt(10)
        out.write("eXIf".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x00)) // only 1 byte of data, but length claims 10
        strip(out.toByteArray())
    }

    private fun containsChunk(data: ByteArray, type: String): Boolean {
        val targetType = chunkType(type)
        var i = 8 // skip PNG signature
        while (i < data.size - 8) {
            val length = readIntBE(data, i)
            val chunkType = readIntBE(data, i + 4)
            if (chunkType == targetType) return true
            i += 12 + length
        }
        return false
    }

    private fun readIntBE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
               ((data[offset + 1].toInt() and 0xFF) shl 16) or
               ((data[offset + 2].toInt() and 0xFF) shl 8) or
               (data[offset + 3].toInt() and 0xFF)
    }

    private fun assertPngStructureValid(data: ByteArray) {
        assertEquals("Must start with PNG signature byte 1", 0x89.toByte(), data[0])
        assertEquals("Must start with PNG signature byte 2", 0x50.toByte(), data[1])
        assertEquals("Must start with PNG signature byte 3", 0x4E.toByte(), data[2])
        assertEquals("Must start with PNG signature byte 4", 0x47.toByte(), data[3])
        assertTrue("Must contain IHDR chunk", containsChunk(data, "IHDR"))
        assertTrue("Must contain IDAT chunk", containsChunk(data, "IDAT"))
        assertTrue("Must contain IEND chunk", containsChunk(data, "IEND"))
    }
}

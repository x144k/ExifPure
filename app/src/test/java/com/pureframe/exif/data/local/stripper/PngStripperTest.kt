package com.pureframe.exif.data.local.stripper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Unit tests for [PngStripper]. */
class PngStripperTest {

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

    /**
     * Builds a minimal synthetically valid 1x1 grayscale PNG:
     * signature -> IHDR -> [optional inserted chunks] -> IDAT -> IEND
     */
    private fun buildMinimalPng(insertBeforeIdat: ByteArray = byteArrayOf()): ByteArray {
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        // IHDR: 1x1, 8-bit grayscale
        val ihdrData = byteArrayOf(
            0x00, 0x00, 0x00, 0x01, // width
            0x00, 0x00, 0x00, 0x01, // height
            0x08, // bit depth
            0x00, // color type (grayscale)
            0x00, // compression method
            0x00, // filter method
            0x00  // interlace method
        )
        val ihdr = writeChunk("IHDR", ihdrData)

        // IDAT: zlib-compressed image data for 1x1 grayscale
        // One scanline: filter byte (0) + 1 pixel byte (0) = [0x00, 0x00]
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
        PngStripper.strip(ByteArrayInputStream(input), out)
        return out.toByteArray()
    }

    /**
     * Scans chunk-by-chunk to determine whether a chunk of the given type exists.
     */
    private fun containsChunk(data: ByteArray, type: String): Boolean {
        val targetType = chunkType(type)
        var i = 8 // skip PNG signature
        while (i < data.size - 8) {
            val length = readIntBE(data, i)
            val chunkType = readIntBE(data, i + 4)
            if (chunkType == targetType) return true
            i += 12 + length // length(4) + type(4) + crc(4) + data(length)
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

    @Test(expected = IllegalArgumentException::class)
    fun strip_missingSignature_throws() {
        val invalid = ByteArray(8) { 0x00 }
        strip(invalid)
    }

    @Test
    fun strip_minimalPng_noMetadata() {
        val original = buildMinimalPng()
        val cleaned = strip(original)
        assertArrayEquals("PNG with no metadata should be unchanged", original, cleaned)
    }

    @Test
    fun strip_exif_removed() {
        val exifData = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(20) { 0x42.toByte() }
        val exifChunk = writeChunk("eXIf", exifData)

        val original = buildMinimalPng(insertBeforeIdat = exifChunk)
        val cleaned = strip(original)

        assertFalse("eXIf chunk should be removed", containsChunk(cleaned, "eXIf"))
        assertPngStructureValid(cleaned)
    }

    @Test
    fun strip_text_removed() {
        val textData = "Title\u0000Test image".toByteArray(Charsets.US_ASCII)
        val textChunk = writeChunk("tEXt", textData)

        val original = buildMinimalPng(insertBeforeIdat = textChunk)
        val cleaned = strip(original)

        assertFalse("tEXt chunk should be removed", containsChunk(cleaned, "tEXt"))
        assertPngStructureValid(cleaned)
    }

    @Test
    fun strip_ztxt_removed() {
        val ztxtData = "Author\u0000Test author".toByteArray(Charsets.US_ASCII)
        val ztxtChunk = writeChunk("zTXt", ztxtData)

        val original = buildMinimalPng(insertBeforeIdat = ztxtChunk)
        val cleaned = strip(original)

        assertFalse("zTXt chunk should be removed", containsChunk(cleaned, "zTXt"))
        assertPngStructureValid(cleaned)
    }

    @Test
    fun strip_itxt_removed() {
        val itxtData = byteArrayOf(0x00) + "en\u0000\u0000Title\u0000Test".toByteArray(Charsets.UTF_8)
        val itxtChunk = writeChunk("iTXt", itxtData)

        val original = buildMinimalPng(insertBeforeIdat = itxtChunk)
        val cleaned = strip(original)

        assertFalse("iTXt chunk should be removed", containsChunk(cleaned, "iTXt"))
        assertPngStructureValid(cleaned)
    }

    @Test
    fun strip_time_removed() {
        val timeData = byteArrayOf(0x07, 0xE5.toByte(), 0x01, 0x01, 0x00, 0x00, 0x00)
        val timeChunk = writeChunk("tIME", timeData)

        val original = buildMinimalPng(insertBeforeIdat = timeChunk)
        val cleaned = strip(original)

        assertFalse("tIME chunk should be removed", containsChunk(cleaned, "tIME"))
        assertPngStructureValid(cleaned)
    }

    @Test
    fun strip_idat_preserved() {
        val exifChunk = writeChunk("eXIf", ByteArray(10) { 0x11.toByte() })
        val textChunk = writeChunk("tEXt", ByteArray(10) { 0x22.toByte() })

        val original = buildMinimalPng(insertBeforeIdat = exifChunk + textChunk)
        val cleaned = strip(original)

        assertPngStructureValid(cleaned)
        assertTrue("IDAT should be preserved", containsChunk(cleaned, "IDAT"))
        assertTrue("IHDR should be preserved", containsChunk(cleaned, "IHDR"))
        assertTrue("IEND should be preserved", containsChunk(cleaned, "IEND"))
    }

    @Test
    fun strip_multipleChunks_removed() {
        val exif = writeChunk("eXIf", ByteArray(10) { 0x11.toByte() })
        val text = writeChunk("tEXt", ByteArray(10) { 0x22.toByte() })
        val ztxt = writeChunk("zTXt", ByteArray(10) { 0x33.toByte() })
        val itxt = writeChunk("iTXt", ByteArray(10) { 0x44.toByte() })
        val time = writeChunk("tIME", ByteArray(10) { 0x55.toByte() })

        val original = buildMinimalPng(insertBeforeIdat = exif + text + ztxt + itxt + time)
        val cleaned = strip(original)

        assertPngStructureValid(cleaned)
        assertFalse("eXIf removed", containsChunk(cleaned, "eXIf"))
        assertFalse("tEXt removed", containsChunk(cleaned, "tEXt"))
        assertFalse("zTXt removed", containsChunk(cleaned, "zTXt"))
        assertFalse("iTXt removed", containsChunk(cleaned, "iTXt"))
        assertFalse("tIME removed", containsChunk(cleaned, "tIME"))
        assertTrue("IDAT preserved", containsChunk(cleaned, "IDAT"))
        assertTrue("IHDR preserved", containsChunk(cleaned, "IHDR"))
        assertTrue("IEND preserved", containsChunk(cleaned, "IEND"))
    }
}

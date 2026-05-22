package com.pureframe.exif.data.local.stripper

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Strips ancillary metadata chunks from PNG without re-compression.
 *
 * Removes `eXIf`, `tEXt`, `zTXt`, `iTXt`, and `tIME` chunks while preserving
 * critical chunks (`IHDR`, `PLTE`, `IDAT`, `IEND`) and color management data.
 */
object PngStripper {

    private const val MAX_CHUNK_LENGTH = 100 * 1024 * 1024 // 100 MB

    private val CHUNK_EXIF = chunkType("eXIf")
    private val CHUNK_TEXT = chunkType("tEXt")
    private val CHUNK_ZTXT = chunkType("zTXt")
    private val CHUNK_ITXT = chunkType("iTXt")
    private val CHUNK_TIME = chunkType("tIME")
    private val DROP_CHUNKS = setOf(CHUNK_EXIF, CHUNK_TEXT, CHUNK_ZTXT, CHUNK_ITXT, CHUNK_TIME)

    private fun chunkType(name: String): Int =
        (name[0].code shl 24) or (name[1].code shl 16) or (name[2].code shl 8) or name[3].code

    /**
     * @param input Raw PNG bytes. Must begin with the 8-byte PNG signature.
     * @param output Stream to write the cleaned PNG.
     * @throws IllegalArgumentException if the input does not start with a valid PNG signature.
     */
    fun strip(input: InputStream, output: OutputStream) {
        val reader = DataInputStream(input.buffered())
        val writer = DataOutputStream(output.buffered())

        val sig = ByteArray(8)
        try {
            reader.readFully(sig)
        } catch (e: java.io.EOFException) {
            throw IllegalStateException("Unexpected EOF reading PNG signature")
        }
        require(
            sig.contentEquals(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                )
            )
        ) { "Not a valid PNG file: signature mismatch" }
        writer.write(sig)

        while (true) {
            val length = try {
                reader.readInt().toLong() and 0xFFFFFFFFL
            } catch (e: java.io.EOFException) {
                throw IllegalStateException("Unexpected EOF reading PNG chunk length")
            }
            if (length > MAX_CHUNK_LENGTH) {
                throw IllegalArgumentException("PNG chunk exceeds maximum allowed size")
            }
            val type = try {
                reader.readInt()
            } catch (e: java.io.EOFException) {
                throw IllegalStateException("Unexpected EOF reading PNG chunk type")
            }
            val data = ByteArray(length.toInt())
            try {
                reader.readFully(data)
            } catch (e: java.io.EOFException) {
                throw IllegalStateException("Unexpected EOF reading PNG chunk data")
            }
            val crc = try {
                reader.readInt()
            } catch (e: java.io.EOFException) {
                throw IllegalStateException("Unexpected EOF reading PNG chunk CRC")
            }

            if (type == chunkType("IEND")) {
                writeChunk(writer, type, data, crc)
                writer.flush()
                return
            }

            if (type !in DROP_CHUNKS) {
                writeChunk(writer, type, data, crc)
            }
        }
    }

    private fun writeChunk(writer: DataOutputStream, type: Int, data: ByteArray, crc: Int) {
        writer.writeInt(data.size)
        writer.writeInt(type)
        writer.write(data)
        writer.writeInt(crc)
    }
}

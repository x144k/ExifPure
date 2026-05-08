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

    /** EXIF chunk type code, packed as big-endian 32-bit integer. */
    private val CHUNK_EXIF = chunkType("eXIf")

    /** Uncompressed text chunk. */
    private val CHUNK_TEXT = chunkType("tEXt")

    /** zlib-compressed text chunk. */
    private val CHUNK_ZTXT = chunkType("zTXt")

    /** International UTF-8 text chunk. */
    private val CHUNK_ITXT = chunkType("iTXt")

    /** Last-modified timestamp chunk. */
    private val CHUNK_TIME = chunkType("tIME")

    /** Packs a 4-character ASCII chunk name into a 32-bit big-endian integer. */
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

        // Validate PNG signature
        val sig = ByteArray(8)
        reader.readFully(sig)
        require(
            sig.contentEquals(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                )
            )
        ) { "Not a valid PNG file: signature mismatch" }
        writer.write(sig)

        // Chunk-by-chunk processing
        while (true) {
            val length = reader.readInt()   // Big-endian unsigned 32-bit
            val type = reader.readInt()      // Big-endian chunk type code
            val data = ByteArray(length)
            reader.readFully(data)
            val crc = reader.readInt()

            // IEND must always be the last chunk — copy it and terminate
            if (type == chunkType("IEND")) {
                writeChunk(writer, type, data, crc)
                writer.flush()
                return
            }

            // Discard metadata chunks; copy everything else (IHDR, PLTE, IDAT, etc.)
            if (type !in setOf(CHUNK_EXIF, CHUNK_TEXT, CHUNK_ZTXT, CHUNK_ITXT, CHUNK_TIME)) {
                writeChunk(writer, type, data, crc)
            }
            // If the type matched a metadata chunk, we simply do not write it —
            // the chunk is dropped from the output stream entirely.
        }
    }

    /** Writes a complete chunk (length, type, data, CRC) in big-endian. */
    private fun writeChunk(writer: DataOutputStream, type: Int, data: ByteArray, crc: Int) {
        writer.writeInt(data.size)
        writer.writeInt(type)
        writer.write(data)
        writer.writeInt(crc)
    }
}

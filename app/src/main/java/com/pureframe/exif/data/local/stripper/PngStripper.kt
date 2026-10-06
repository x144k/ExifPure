package com.pureframe.exif.data.local.stripper

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Lossless PNG metadata stripper.
 *
 * PNG files are structured as a sequence of **chunks**, each with a rigid format:
 * ```
 * [4 bytes: data length (big-endian)] [4 bytes: chunk type] [N bytes: data] [4 bytes: CRC32]
 * ```
 *
 * The chunk type is a 4-character ASCII code packed into a 32-bit integer. The first
 * character's case indicates whether the chunk is **critical** (uppercase) or
 * **ancillary** (lowercase). Ancillary chunks can be safely discarded without affecting
 * image decodeability.
 *
 * ## Chunks we strip (all ancillary metadata)
 * | Type | Name | Content |
 * |------|------|---------|
 * | `eXIf` | Exif metadata | Camera info, GPS, exposure data |
 * | `tEXt` | Text metadata | Title, author, description |
 * | `zTXt` | Compressed text | Same as tEXt, zlib-compressed |
 * | `iTXt` | International text | UTF-8 text with language tag |
 * | `tIME` | Last-modified time | Timestamp |
 *
 * ## Chunks we **never** touch (critical)
 * | Type | Name | Why it must be preserved |
 * |------|------|--------------------------|
 * | `IHDR` | Image header | Dimensions, bit depth, color type |
 * | `PLTE` | Palette | Required for indexed-color images |
 * | `IDAT` | Image data | zlib-compressed pixel data |
 * | `IEND` | End marker | File terminator |
 * | `tRNS` | Transparency | Alpha for palette or grayscale |
 * | `cHRM`, `gAMA`, `sRGB`, `iCCP` | Color management | Affect rendering; not "metadata" in the privacy sense |
 *
 * ## Lossless guarantee
 * Because PNG uses DEFLATE compression for pixel data inside `IDAT` chunks, and we
 * only remove ancillary chunks while copying `IDAT` verbatim, the decoded pixels are
 * identical. No re-compression occurs.
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

    /**
     * Packs a 4-character ASCII chunk name into a 32-bit big-endian integer.
     *
     * Example: `chunkType("eXIf")` → `0x65584966`.
     */
    private fun chunkType(name: String): Int =
        (name[0].code shl 24) or (name[1].code shl 16) or (name[2].code shl 8) or name[3].code

    /**
     * Strips metadata chunks from a PNG stream.
     *
     * @param input  Raw PNG bytes. Must begin with the 8-byte PNG signature.
     * @param output Stream to write the cleaned PNG.
     * @throws IllegalArgumentException if the input does not start with a valid PNG signature.
     */
    fun strip(input: InputStream, output: OutputStream) {
        val reader = DataInputStream(input.buffered())
        val writer = DataOutputStream(output.buffered())

        // ── Validate PNG signature ───────────────────────────────────────
        // Signature: 89 50 4E 47 0D 0A 1A 0A (hex)
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

        // ── Chunk-by-chunk processing ────────────────────────────────────
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

    /**
     * Writes a complete chunk (length, type, data, CRC) to the output stream.
     *
     * All fields are big-endian per the PNG specification.
     */
    private fun writeChunk(writer: DataOutputStream, type: Int, data: ByteArray, crc: Int) {
        writer.writeInt(data.size)
        writer.writeInt(type)
        writer.write(data)
        writer.writeInt(crc)
    }
}

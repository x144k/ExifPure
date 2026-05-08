package com.pureframe.exif.data.local.stripper

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * Strips only GPS coordinates from PNG EXIF while preserving other metadata.
 *
 * Locates the `eXIf` chunk, zeros the GPS IFD entry count, and recalculates
 * the chunk CRC32. All other chunks are copied verbatim.
 *
 * Note: forensic tools may still recover GPS data from raw TIFF bytes.
 */
object PngGpsStripper {

    /** EXIF chunk type code, packed as big-endian 32-bit integer. */
    private val CHUNK_EXIF = chunkType("eXIf")

    /** Packs a 4-character ASCII chunk name into a 32-bit big-endian integer. */
    private fun chunkType(name: String): Int =
        (name[0].code shl 24) or (name[1].code shl 16) or (name[2].code shl 8) or name[3].code

    /**
     * @param input Raw PNG bytes. Must begin with the 8-byte PNG signature.
     * @param output Stream to write the GPS-scrubbed PNG.
     * @throws IllegalArgumentException if the input is not a valid PNG.
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
            val length = reader.readInt()
            val type = reader.readInt()
            val data = ByteArray(length)
            reader.readFully(data)
            val crc = reader.readInt()

            // IEND is always last — copy and finish
            if (type == chunkType("IEND")) {
                writeChunk(writer, type, data, crc)
                writer.flush()
                return
            }

            if (type == CHUNK_EXIF) {
                // Mutate the EXIF payload to remove GPS, then recalculate CRC
                stripGpsFromExif(data)
                val typeBytes = byteArrayOf(
                    (type shr 24).toByte(), (type shr 16).toByte(), (type shr 8).toByte(), type.toByte()
                )
                val newCrc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()
                writeChunk(writer, type, data, newCrc)
            } else {
                // All other chunks — copy verbatim (including original CRC)
                writeChunk(writer, type, data, crc)
            }
        }
    }

    /**
     * Zeros the GPS IFD entry count inside a TIFF payload.
     *
     * @param data The raw TIFF/EXIF payload from the `eXIf` chunk (no PNG framing).
     */
    private fun stripGpsFromExif(data: ByteArray) {
        if (data.size < 8) return

        // Determine byte order
        val littleEndian = when {
            data[0] == 'I'.code.toByte() && data[1] == 'I'.code.toByte() -> true
            data[0] == 'M'.code.toByte() && data[1] == 'M'.code.toByte() -> false
            else -> return
        }

        fun readInt16(offset: Int): Int {
            return if (littleEndian) {
                (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            } else {
                ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            }
        }

        fun readInt32(offset: Int): Int {
            return if (littleEndian) {
                (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
            } else {
                ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
            }
        }

        // Locate IFD0 and scan for GPS pointer
        val ifd0Offset = readInt32(4)
        if (ifd0Offset < 0 || ifd0Offset >= data.size - 2) return

        val entryCount = readInt16(ifd0Offset)
        if (entryCount < 0 || entryCount > 1000) return

        for (i in 0 until entryCount) {
            val entryOffset = ifd0Offset + 2 + (i * 12)
            if (entryOffset + 12 > data.size) break

            val tag = readInt16(entryOffset)
            if (tag == 0x8825) {
                val gpsIfdOffset = readInt32(entryOffset + 8)
                if (gpsIfdOffset >= 0 && gpsIfdOffset + 2 <= data.size) {
                    data[gpsIfdOffset] = 0
                    data[gpsIfdOffset + 1] = 0
                }
                break
            }
        }
    }

    /** Writes a complete PNG chunk (length, type, data, CRC) in big-endian. */
    private fun writeChunk(writer: DataOutputStream, type: Int, data: ByteArray, crc: Int) {
        writer.writeInt(data.size)
        writer.writeInt(type)
        writer.write(data)
        writer.writeInt(crc)
    }
}

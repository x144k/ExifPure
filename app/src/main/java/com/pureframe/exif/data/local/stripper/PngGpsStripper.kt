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

    private const val MAX_CHUNK_LENGTH = StripperConstants.MAX_CHUNK_LENGTH

    private val CHUNK_EXIF = chunkType("eXIf")

    private fun chunkType(name: String): Int =
        (name[0].code shl 24) or (name[1].code shl 16) or (name[2].code shl 8) or name[3].code

    /**
     * @param input Raw PNG bytes. Must begin with the 8-byte PNG signature.
     * @param output Stream to write the GPS-scrubbed PNG.
     * @throws IllegalArgumentException if the input is not a valid PNG or a chunk exceeds the size limit.
     * @throws IllegalStateException if an unexpected EOF occurs while reading chunk data.
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

            if (type == CHUNK_EXIF) {
                stripGpsFromExif(data)
                val typeBytes = byteArrayOf(
                    (type shr 24).toByte(), (type shr 16).toByte(), (type shr 8).toByte(), type.toByte()
                )
                val newCrc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()
                writeChunk(writer, type, data, newCrc)
            } else {
                writeChunk(writer, type, data, crc)
            }
        }
    }

    private fun stripGpsFromExif(data: ByteArray) {
        if (data.size < 8) return

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

        val ifd0Offset = readInt32(4)
        if (ifd0Offset < 0) return
        if (ifd0Offset > data.size - 2) return

        val entryCount = readInt16(ifd0Offset)
        if (entryCount < 0 || entryCount > 1000) return

        for (i in 0 until entryCount) {
            val entryOffset = ifd0Offset + 2 + (i * 12)
            if (entryOffset < 0 || entryOffset > data.size - 12) break

            val tag = readInt16(entryOffset)
            if (tag == 0x8825) {
                val type = readInt16(entryOffset + 2)
                val count = readInt32(entryOffset + 4)
                if (type != 4 || count != 1) break

                val gpsIfdOffset = readInt32(entryOffset + 8)
                if (gpsIfdOffset <= 0) break
                if (gpsIfdOffset > data.size - 2) break
                // Reject pointers that overlap IFD0 to avoid corrupting the whole EXIF block.
                if (gpsIfdOffset <= ifd0Offset + 2) break

                data[gpsIfdOffset] = 0
                data[gpsIfdOffset + 1] = 0
                break
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

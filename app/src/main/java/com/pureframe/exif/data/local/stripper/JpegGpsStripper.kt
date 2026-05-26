package com.pureframe.exif.data.local.stripper

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Strips only GPS coordinates from JPEG EXIF while preserving other metadata.
 *
 * Parses the TIFF structure inside the APP1 segment, locates the GPS IFD via tag
 * `0x8825`, and zeros its entry count so standard readers skip it. This avoids
 * the complex offset recalculation that full removal would require.
 *
 * Note: forensic tools that scan raw TIFF bytes may still recover GPS data.
 * For absolute removal, use [JpegStripper] instead.
 */
object JpegGpsStripper {

    /**
     * @param input Raw JPEG bytes. Must begin with `0xFFD8`.
     * @param output Stream to write the GPS-scrubbed JPEG.
     * @throws IllegalArgumentException if the input is not a valid JPEG.
     * @throws IllegalStateException if an unexpected EOF occurs while reading markers or entropy data.
     */
    fun strip(input: InputStream, output: OutputStream) {
        val reader = BufferedInputStream(input)
        val writer = BufferedOutputStream(output)

        require(reader.read() == 0xFF && reader.read() == 0xD8) {
            "Invalid JPEG: expected SOI marker 0xFFD8"
        }
        writer.write(0xFF)
        writer.write(0xD8)

        var state = State.MARKERS

        while (true) {
            when (state) {
                State.MARKERS -> {
                    val marker = readMarker(reader)
                    when {
                        marker == 0xD9 -> {
                            writer.write(0xFF)
                            writer.write(0xD9)
                            writer.flush()
                            return
                        }
                        marker == 0xDA -> {
                            state = State.ENTROPY
                            copySegment(reader, writer, marker)
                        }
                        marker == 0xE1 -> {
                            val segmentData = readSegmentData(reader)
                            if (segmentData.size > 6 &&
                                segmentData.copyOfRange(0, 6).contentEquals("Exif\u0000\u0000".toByteArray())
                            ) {
                                stripGpsFromExif(segmentData)
                            }
                            writer.write(0xFF)
                            writer.write(0xE1)
                            writeInt16(writer, segmentData.size + 2)
                            writer.write(segmentData)
                        }
                        else -> copySegment(reader, writer, marker)
                    }
                }
                State.ENTROPY -> {
                    val b = reader.read()
                    if (b == -1) throw IllegalStateException("Unexpected EOF in JPEG entropy data")
                    if (b == 0xFF) {
                        val next = reader.read()
                        if (next == -1) throw IllegalStateException("Unexpected EOF in JPEG entropy data")
                        when (next) {
                            0x00 -> {
                                writer.write(0xFF)
                                writer.write(0x00)
                            }
                            in 0xD0..0xD7 -> {
                                writer.write(0xFF)
                                writer.write(next)
                            }
                            0xD9 -> {
                                writer.write(0xFF)
                                writer.write(0xD9)
                                writer.flush()
                                return
                            }
                            else -> {
                                writer.write(0xFF)
                                writer.write(next)
                            }
                        }
                    } else writer.write(b)
                }
            }
        }
    }

    private fun stripGpsFromExif(data: ByteArray) {
        val tiffStart = 6
        if (data.size < tiffStart + 8) return

        val littleEndian = when {
            data[tiffStart] == 'I'.code.toByte() && data[tiffStart + 1] == 'I'.code.toByte() -> true
            data[tiffStart] == 'M'.code.toByte() && data[tiffStart + 1] == 'M'.code.toByte() -> false
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

        val ifd0Offset = readInt32(tiffStart + 4)
        if (ifd0Offset < 0) return
        val ifd0Start = tiffStart + ifd0Offset
        if (ifd0Start < 0 || ifd0Start > data.size - 2) return

        val entryCount = readInt16(ifd0Start)
        if (entryCount < 0 || entryCount > 1000) return

        for (i in 0 until entryCount) {
            val entryOffset = ifd0Start + 2 + (i * 12)
            if (entryOffset < 0 || entryOffset > data.size - 12) break

            val tag = readInt16(entryOffset)
            if (tag == 0x8825) {
                val type = readInt16(entryOffset + 2)
                val count = readInt32(entryOffset + 4)
                if (type != 4 || count != 1) break

                val gpsIfdOffset = readInt32(entryOffset + 8)
                if (gpsIfdOffset <= 0) break
                val gpsIfdStart = tiffStart + gpsIfdOffset
                if (gpsIfdStart < 0 || gpsIfdStart > data.size - 2) break
                // Reject pointers that overlap IFD0 to avoid corrupting the whole EXIF block.
                if (gpsIfdStart <= ifd0Start + 2) break

                data[gpsIfdStart] = 0
                data[gpsIfdStart + 1] = 0
                break
            }
        }
    }

    private fun readMarker(reader: BufferedInputStream): Int {
        while (true) {
            val b = reader.read()
            if (b == -1) throw IllegalStateException("Unexpected EOF while reading JPEG marker")
            if (b != 0xFF) continue
            var m = reader.read()
            if (m == -1) throw IllegalStateException("Unexpected EOF while reading JPEG marker")
            while (m == 0xFF) {
                m = reader.read()
                if (m == -1) throw IllegalStateException("Unexpected EOF while reading JPEG marker")
            }
            if (m != 0x00) return m
        }
    }

    private fun readSegmentData(reader: BufferedInputStream): ByteArray {
        val lenHigh = reader.read()
        val lenLow = reader.read()
        if (lenHigh == -1 || lenLow == -1) {
            throw IllegalStateException("Unexpected EOF reading segment length")
        }
        val length = ((lenHigh shl 8) or lenLow) - 2
        if (length < 0) {
            throw IllegalStateException("Invalid JPEG segment length")
        }
        if (length == 0) return byteArrayOf()
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val r = reader.read(data, read, length - read)
            if (r == -1) {
                throw IllegalStateException("Unexpected EOF reading segment payload")
            }
            read += r
        }
        return data
    }

    private fun writeInt16(writer: BufferedOutputStream, value: Int) {
        writer.write((value shr 8) and 0xFF)
        writer.write(value and 0xFF)
    }

    private fun copySegment(reader: BufferedInputStream, writer: BufferedOutputStream, marker: Int) {
        writer.write(0xFF)
        writer.write(marker)
        if (marker in 0xD0..0xD9 || marker == 0xD8 || marker == 0x01) return
        val lenHigh = reader.read()
        val lenLow = reader.read()
        require(lenHigh != -1 && lenLow != -1) { "Unexpected EOF reading segment length" }
        val length = (lenHigh shl 8) or lenLow
        require(length >= 2) { "Invalid JPEG segment length: $length" }
        writer.write(lenHigh)
        writer.write(lenLow)
        val buf = ByteArray(8192)
        var remaining = length - 2
        while (remaining > 0) {
            val r = reader.read(buf, 0, minOf(buf.size, remaining))
            require(r > 0) { "Unexpected EOF copying segment payload" }
            writer.write(buf, 0, r)
            remaining -= r
        }
    }

    private enum class State { MARKERS, ENTROPY }
}

package com.pureframe.exif.data.local.stripper

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * GPS-only stripper for JPEG files.
 *
 * Unlike [JpegStripper], which removes the entire APP1/EXIF segment, this class
 * **preserves all EXIF data except GPS coordinates**. It achieves this by parsing
 * the TIFF structure inside the APP1 segment and corrupting the GPS IFD so that
 * standard EXIF readers skip it, while leaving all other IFD entries intact.
 *
 * ## Strategy overview
 * 1. Locate the APP1 segment containing the EXIF payload.
 * 2. Parse the TIFF header to determine byte order (little-endian or big-endian).
 * 3. Read IFD0 (Image File Directory 0) and scan its entries for tag `0x8825`
 *    (GPSInfo IFD Pointer).
 * 4. Overwrite the GPS IFD's entry-count field with zeros, effectively declaring
 *    the IFD empty. Standard readers then skip the GPS block entirely.
 * 5. Recalculate and rewrite the APP1 segment with the modified payload.
 *
 * ## Why corruption instead of full removal?
 * Removing individual IFD entries would require recalculating offsets for all
 * subsequent entries and updating the TIFF header's offset chain — a complex and
 * error-prone rewrite. Zeroing the entry count is a single-byte change that
 * produces the same observable result (no GPS data visible to readers) without
 * shifting any offsets or changing segment lengths.
 *
 * ## Caveat
 * This is a **best-effort** approach. Sophisticated forensic tools that parse
 * raw TIFF structures may still detect the original GPS IFD offset and recover
 * coordinates from the unmodified payload bytes. For absolute GPS removal,
 * use [JpegStripper] (full EXIF removal) or re-encode the image.
 */
object JpegGpsStripper {

    /**
     * Strips GPS coordinates from a JPEG while preserving all other EXIF metadata.
     *
     * @param input  Raw JPEG bytes. Must begin with `0xFFD8`.
     * @param output Stream to write the GPS-scrubbed JPEG.
     * @throws IllegalArgumentException if the input is not a valid JPEG.
     */
    fun strip(input: InputStream, output: OutputStream) {
        val reader = BufferedInputStream(input)
        val writer = BufferedOutputStream(output)

        // ── Validate SOI ───────────────────────────────────────────────────
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
                            // APP1 segment — may contain EXIF. Read payload, strip GPS, rewrite.
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
                    if (b == -1) break
                    if (b == 0xFF) {
                        val next = reader.read()
                        if (next == -1) break
                        when (next) {
                            0x00 -> { writer.write(0xFF); writer.write(0x00) }
                            in 0xD0..0xD7 -> { writer.write(0xFF); writer.write(next) }
                            0xD9 -> { writer.write(0xFF); writer.write(0xD9); writer.flush(); return }
                            else -> { writer.write(0xFF); writer.write(next) }
                        }
                    } else writer.write(b)
                }
            }
        }
    }

    /**
     * Corrupts the GPS IFD inside a TIFF/EXIF payload by zeroing its entry count.
     *
     * The TIFF structure begins after the 6-byte "Exif\0\0" header. The layout is:
     * ```
     * [2 bytes: byte order "II" or "MM"]
     * [2 bytes: magic 0x002A]
     * [4 bytes: offset to IFD0]
     * [IFD0: 2-byte entry count + N × 12-byte entries + 4-byte next-IFD offset]
     * ```
     *
     * Each IFD entry is 12 bytes:
     * ```
     * [2 bytes: tag ID] [2 bytes: type] [4 bytes: count/values] [4 bytes: value or offset]
     * ```
     *
     * Tag `0x8825` (GPSInfo IFD Pointer) contains the offset to the GPS IFD.
     * We locate that IFD and overwrite its first 2 bytes (entry count) with zeros.
     * Standard EXIF readers see count=0 and skip the entire IFD.
     *
     * @param data The APP1 segment payload (including the 6-byte Exif header).
     */
    private fun stripGpsFromExif(data: ByteArray) {
        val tiffStart = 6
        if (data.size < tiffStart + 8) return

        // ── Determine byte order ─────────────────────────────────────────
        val littleEndian = when {
            data[tiffStart] == 'I'.code.toByte() && data[tiffStart + 1] == 'I'.code.toByte() -> true
            data[tiffStart] == 'M'.code.toByte() && data[tiffStart + 1] == 'M'.code.toByte() -> false
            else -> return // Not a valid TIFF header
        }

        // ── Helper lambdas for multi-byte reads ──────────────────────────
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

        // ── Locate IFD0 ──────────────────────────────────────────────────
        val ifd0Offset = readInt32(tiffStart + 4)
        val ifd0Start = tiffStart + ifd0Offset
        if (ifd0Start < 0 || ifd0Start >= data.size - 2) return

        val entryCount = readInt16(ifd0Start)
        if (entryCount < 0 || entryCount > 1000) return

        // ── Scan IFD0 entries for GPS pointer (tag 0x8825) ────────────
        for (i in 0 until entryCount) {
            val entryOffset = ifd0Start + 2 + (i * 12)
            if (entryOffset + 12 > data.size) break

            val tag = readInt16(entryOffset)
            if (tag == 0x8825) {
                // Found GPSInfo IFD Pointer. The value/offset field is at entryOffset+8.
                val gpsIfdOffset = readInt32(entryOffset + 8)
                val gpsIfdStart = tiffStart + gpsIfdOffset

                // Sanity-check the GPS IFD location, then zero its entry count.
                if (gpsIfdStart >= 0 && gpsIfdStart + 2 <= data.size) {
                    data[gpsIfdStart] = 0
                    data[gpsIfdStart + 1] = 0
                }
                break
            }
        }
    }

    /** Reads the next valid marker, skipping padding bytes. */
    private fun readMarker(reader: BufferedInputStream): Int {
        while (true) {
            if (reader.read() != 0xFF) continue
            var m = reader.read()
            while (m == 0xFF) m = reader.read()
            if (m != 0x00) return m
        }
    }

    /** Reads a length-prefixed segment's payload (excluding the length field itself). */
    private fun readSegmentData(reader: BufferedInputStream): ByteArray {
        val lenHigh = reader.read()
        val lenLow = reader.read()
        if (lenHigh == -1 || lenLow == -1) return byteArrayOf()
        val length = ((lenHigh shl 8) or lenLow) - 2
        if (length <= 0) return byteArrayOf()
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val r = reader.read(data, read, length - read)
            if (r == -1) break
            read += r
        }
        return data.copyOf(read)
    }

    /** Writes a 16-bit big-endian integer. */
    private fun writeInt16(writer: BufferedOutputStream, value: Int) {
        writer.write((value shr 8) and 0xFF)
        writer.write(value and 0xFF)
    }

    /** Copies a segment (marker + length + payload) verbatim. */
    private fun copySegment(reader: BufferedInputStream, writer: BufferedOutputStream, marker: Int) {
        writer.write(0xFF)
        writer.write(marker)
        if (marker in 0xD0..0xD9 || marker == 0xD8 || marker == 0x01) return
        val lenHigh = reader.read(); val lenLow = reader.read()
        require(lenHigh != -1 && lenLow != -1)
        val length = (lenHigh shl 8) or lenLow
        writer.write(lenHigh); writer.write(lenLow)
        val buf = ByteArray(8192)
        var remaining = length - 2
        while (remaining > 0) {
            val r = reader.read(buf, 0, minOf(buf.size, remaining))
            require(r > 0)
            writer.write(buf, 0, r)
            remaining -= r
        }
    }

    private enum class State { MARKERS, ENTROPY }
}

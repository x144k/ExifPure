package com.pureframe.exif.data.local.stripper

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Strips EXIF, IPTC, and comment segments from JPEG without re-compression.
 *
 * Walks the file segment-by-segment, discarding metadata-bearing APP1/APP13/COM
 * segments while copying everything else (SOF, DQT, DHT, SOS, entropy) verbatim.
 *
 * Inside entropy data, literal `0xFF` bytes are escaped as `0xFF00`. The parser
 * respects this escape so it does not misinterpret embedded `0xFF` values as markers.
 */
object JpegStripper {

    /**
     * @param input Raw JPEG bytes. Must begin with `0xFFD8` (SOI).
     * @param output Stream to write the cleaned JPEG.
     * @throws IllegalArgumentException if the input does not start with a valid SOI.
     */
    fun strip(input: InputStream, output: OutputStream) {
        val reader = BufferedInputStream(input)
        val writer = BufferedOutputStream(output)

        // Validate SOI
        require(reader.read() == 0xFF && reader.read() == 0xD8) {
            "Invalid JPEG: expected SOI marker 0xFFD8 at offset 0"
        }
        writer.write(0xFF)
        writer.write(0xD8)

        var state = State.MARKERS

        while (true) {
            when (state) {
                State.MARKERS -> {
                    val marker = readMarker(reader)
                    when (marker) {
                        0xD9 -> {
                            writer.write(0xFF)
                            writer.write(0xD9)
                            writer.flush()
                            return
                        }
                        0xDA -> {
                            state = State.ENTROPY
                            copySegment(reader, writer, marker)
                        }
                        0xE1, 0xED, 0xFE -> skipSegment(reader)
                        else -> copySegment(reader, writer, marker)
                    }
                }

                State.ENTROPY -> {
                    val b = reader.read()
                    if (b == -1) break // Unexpected EOF — abort gracefully

                    if (b == 0xFF) {
                        val next = reader.read()
                        if (next == -1) break

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
                    } else {
                        writer.write(b)
                    }
                }
            }
        }
    }

    /**
     * Reads the next valid marker, skipping padding bytes.
     *
     * @return The marker identifier byte (e.g., `0xE1` for APP1).
     */
    private fun readMarker(reader: BufferedInputStream): Int {
        while (true) {
            if (reader.read() != 0xFF) continue
            var m = reader.read()
            while (m == 0xFF) m = reader.read() // Skip padding
            if (m != 0x00) return m             // 0xFF00 is not a marker
        }
    }

    /**
     * Copies a length-prefixed segment including its marker.
     *
     * @param marker The marker byte that was already read (e.g., `0xDB` for DQT).
     */
    private fun copySegment(reader: BufferedInputStream, writer: BufferedOutputStream, marker: Int) {
        writer.write(0xFF)
        writer.write(marker)

        if (marker in 0xD0..0xD9 || marker == 0xD8 || marker == 0x01) return

        val lenHigh = reader.read()
        val lenLow = reader.read()
        require(lenHigh != -1 && lenLow != -1) { "Unexpected EOF reading segment length" }

        val length = (lenHigh shl 8) or lenLow
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

    /** Skips a length-prefixed segment entirely. */
    private fun skipSegment(reader: BufferedInputStream) {
        val lenHigh = reader.read()
        val lenLow = reader.read()
        if (lenHigh == -1 || lenLow == -1) return
        val length = (lenHigh shl 8) or lenLow
        reader.skip((length - 2).toLong())
    }

    /** Parser state: either reading segment headers or inside entropy data. */
    private enum class State { MARKERS, ENTROPY }
}

package com.pureframe.exif.data.local.stripper

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Lossless JPEG metadata stripper.
 *
 * JPEG files are structured as a sequence of **segments** separated by **markers**.
 * A marker is a two-byte code beginning with `0xFF` (e.g., `0xFFD8` = Start Of Image).
 * Between markers lies either structured segment data (length-prefixed) or raw entropy
 * (Huffman-encoded image data). This parser walks the file segment-by-segment and
 * discards metadata-bearing segments while copying everything else verbatim.
 *
 * ## JPEG segment layout
 * ```
 * [SOI: 0xFFD8] ──► [APP0: 0xFFE0 + length + data] ──► ... ──► [SOS: 0xFFDA + length + data]
 *                    ▲
 *                    └── entropy data (no length prefix; terminated by next marker)
 * ```
 *
 * ## Segments we strip
 * | Marker | Name | Purpose |
 * |--------|------|---------|
 * | `0xFFE1` | APP1 / EXIF | Camera metadata, GPS, thumbnails |
 * | `0xFFED` | APP13 / IPTC | Photo agency keywords, captions |
 * | `0xFFFE` | COM | Free-form text comments |
 *
 * ## Segments we **never** touch
 * | Marker | Name | Why it must be preserved |
 * |--------|------|--------------------------|
 * | `0xFFD8` | SOI | Start Of Image — file header |
 * | `0xFFC0`–`0xFFC2` | SOF0–SOF2 | Frame dimensions, component count, sampling |
 * | `0xFFDB` | DQT | Quantization tables — affect pixel values |
 * | `0xFFC4` | DHT | Huffman tables — required to decode entropy |
 * | `0xFFDA` | SOS | Start Of Scan — entropy follows immediately |
 * | `0xFFD9` | EOI | End Of Image — file terminator |
 *
 * ## The entropy state machine
 * Inside entropy data, `0xFF` is a **reserved byte**. If the encoder produces a literal
 * `0xFF`, it must be followed by `0x00` (`0xFF00` = escaped literal). Any other byte after
 * `0xFF` is interpreted as a marker. We must **not** treat `0xFF00` as a segment boundary.
 *
 * ## Lossless guarantee
 * Because we only remove entire APP1/APP13/COM segments and copy all others (including
 * DQT, DHT, SOF, SOS, and entropy) byte-for-byte, the resulting image decodes to the
 * exact same pixel values as the original. No re-compression occurs.
 */
object JpegStripper {

    /**
     * Strips EXIF, IPTC, and comment segments from a JPEG stream.
     *
     * @param input  Raw JPEG bytes. Must begin with `0xFFD8` (SOI).
     * @param output Stream to write the cleaned JPEG.
     * @throws IllegalArgumentException if the input does not start with a valid SOI.
     */
    fun strip(input: InputStream, output: OutputStream) {
        val reader = BufferedInputStream(input)
        val writer = BufferedOutputStream(output)

        // ── Validate Start Of Image ──────────────────────────────────────────
        require(reader.read() == 0xFF && reader.read() == 0xD8) {
            "Invalid JPEG: expected SOI marker 0xFFD8 at offset 0"
        }
        writer.write(0xFF)
        writer.write(0xD8)

        var state = State.MARKERS

        while (true) {
            when (state) {
                // ── MARKERS state: read segment headers ───────────────────────
                State.MARKERS -> {
                    val marker = readMarker(reader)
                    when (marker) {
                        // EOI (End Of Image) — flush and finish
                        0xD9 -> {
                            writer.write(0xFF)
                            writer.write(0xD9)
                            writer.flush()
                            return
                        }

                        // SOS (Start Of Scan) — entropy data follows
                        // Transition to ENTROPY state; copy SOS segment header
                        0xDA -> {
                            state = State.ENTROPY
                            copySegment(reader, writer, marker)
                        }

                        // Metadata segments to discard
                        // 0xE1 = APP1 (EXIF/XMP), 0xED = APP13 (IPTC), 0xFE = COM
                        0xE1, 0xED, 0xFE -> skipSegment(reader)

                        // All other segments (DQT, DHT, SOF, DRI, etc.) — copy verbatim
                        else -> copySegment(reader, writer, marker)
                    }
                }

                // ── ENTROPY state: raw Huffman-encoded scan data ──────────────
                // In this state, 0xFF is special. We must distinguish:
                //   • 0xFF 0x00 → escaped literal 0xFF (part of entropy)
                //   • 0xFF 0xD0–0xD7 → restart markers (RST0–RST7, valid in entropy)
                //   • 0xFF 0xD9 → EOI (terminate)
                //   • 0xFF <anything else> → regular marker (should not happen mid-scan)
                State.ENTROPY -> {
                    val b = reader.read()
                    if (b == -1) break // Unexpected EOF — abort gracefully

                    if (b == 0xFF) {
                        val next = reader.read()
                        if (next == -1) break

                        when (next) {
                            // Escaped literal 0xFF — write both bytes
                            0x00 -> {
                                writer.write(0xFF)
                                writer.write(0x00)
                            }
                            // Restart markers — valid inside entropy, preserve
                            in 0xD0..0xD7 -> {
                                writer.write(0xFF)
                                writer.write(next)
                            }
                            // EOI — terminate
                            0xD9 -> {
                                writer.write(0xFF)
                                writer.write(0xD9)
                                writer.flush()
                                return
                            }
                            // Any other marker — copy it (will transition back to MARKERS)
                            else -> {
                                writer.write(0xFF)
                                writer.write(next)
                            }
                        }
                    } else {
                        // Regular entropy byte
                        writer.write(b)
                    }
                }
            }
        }
    }

    /**
     * Reads the next valid marker from the stream.
     *
     * JPEG allows padding (`0xFF`) between markers. This method skips any number of
     * consecutive `0xFF` bytes and returns the first non-`0xFF`, non-`0x00` byte.
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
     * Copies a length-prefixed segment from [reader] to [writer], including its marker.
     *
     * Most markers (except SOI, EOI, and padding) are followed by a 2-byte big-endian
     * length field that includes itself. We read the length, then copy exactly that
     * many bytes minus 2 (the length field itself).
     *
     * @param marker The marker byte that was already read (e.g., `0xDB` for DQT).
     */
    private fun copySegment(reader: BufferedInputStream, writer: BufferedOutputStream, marker: Int) {
        writer.write(0xFF)
        writer.write(marker)

        // Standalone markers have no length field
        if (marker in 0xD0..0xD9 || marker == 0xD8 || marker == 0x01) return

        val lenHigh = reader.read()
        val lenLow = reader.read()
        require(lenHigh != -1 && lenLow != -1) { "Unexpected EOF reading segment length" }

        val length = (lenHigh shl 8) or lenLow
        writer.write(lenHigh)
        writer.write(lenLow)

        // Copy the segment payload (length includes the 2 length bytes, so subtract 2)
        val buf = ByteArray(8192)
        var remaining = length - 2
        while (remaining > 0) {
            val r = reader.read(buf, 0, minOf(buf.size, remaining))
            require(r > 0) { "Unexpected EOF copying segment payload" }
            writer.write(buf, 0, r)
            remaining -= r
        }
    }

    /**
     * Skips a length-prefixed segment entirely (used for metadata segments we discard).
     *
     * Reads the 2-byte length field and advances the stream by that amount.
     */
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

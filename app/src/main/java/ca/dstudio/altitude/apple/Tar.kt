package ca.dstudio.altitude.apple

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Reads one file out of a tar stream (POSIX ustar), without unpacking the rest. */
object Tar {

    /** The largest entry [entry] reads. */
    const val MAX_ENTRY_BYTES = 16 * 1024 * 1024

    private const val BLOCK = 512

    /**
     * The bytes of the regular file [name] in [input], read up to it and no further; null when the
     * archive holds no such file. Throws on a cut-short archive and on an entry over [MAX_ENTRY_BYTES].
     */
    @Throws(IOException::class)
    fun entry(input: InputStream, name: String): ByteArray? {
        val header = ByteArray(BLOCK)
        while (true) {
            val read = readFully(input, header)
            if (read == 0) return null
            if (read < BLOCK) throw EOFException("a tar header cut short")
            if (header.all { it == 0.toByte() }) return null
            val size = size(header)
            val type = header[156].toInt()
            if ((type == '0'.code || type == 0) && nameOf(header) == name) {
                if (size > MAX_ENTRY_BYTES) throw IOException("$name is $size bytes, over $MAX_ENTRY_BYTES")
                val bytes = ByteArray(size.toInt())
                if (readFully(input, bytes) < bytes.size) throw EOFException("$name cut short")
                return bytes
            }
            skipFully(input, (size + BLOCK - 1) / BLOCK * BLOCK)
        }
    }

    /** The ustar prefix and name fields, joined; a leading `./` dropped. */
    private fun nameOf(header: ByteArray): String {
        val name = field(header, 0, 100)
        // POSIX "ustar\0" alone: an old GNU header ("ustar  ") keeps other fields where the prefix goes.
        val prefix = if (field(header, 257, 6) == "ustar") field(header, 345, 155) else ""
        return (if (prefix.isEmpty()) name else "$prefix/$name").removePrefix("./")
    }

    private fun size(header: ByteArray): Long {
        // The base-256 form, for files over 8 GB, is never a file worth reading here.
        if ((header[124].toInt() and 0x80) != 0) throw IOException("a tar entry too large to read")
        return field(header, 124, 12).trim().ifEmpty { "0" }.toLongOrNull(8)
            ?: throw IOException("a tar header with an unreadable size")
    }

    private fun field(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && header[end] != 0.toByte()) end++
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    /** Reads until [buffer] is full or the stream ends; the count read. */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
            } else {
                if (input.read() < 0) throw EOFException("a tar entry cut short")
                left--
            }
        }
    }
}

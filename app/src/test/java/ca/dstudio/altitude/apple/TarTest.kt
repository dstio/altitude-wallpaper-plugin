package ca.dstudio.altitude.apple

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class TarTest {

    /** A ustar header for a regular file, its checksum filled in as tar writes it. */
    private fun header(name: String, size: Long): ByteArray {
        val header = ByteArray(512)
        name.toByteArray().copyInto(header, 0)
        "0000644".toByteArray().copyInto(header, 100)
        "%011o".format(size).toByteArray().copyInto(header, 124)
        header[156] = '0'.code.toByte()
        "ustar".toByteArray().copyInto(header, 257)
        "00".toByteArray().copyInto(header, 263)
        " ".repeat(8).toByteArray().copyInto(header, 148)
        "%06o\u0000 ".format(header.sumOf { it.toInt() and 0xFF }).toByteArray().copyInto(header, 148)
        return header
    }

    private fun tar(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((name, data) in entries) {
            out.write(header(name, data.size.toLong()))
            out.write(data)
            out.write(ByteArray((512 - data.size % 512) % 512))
        }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    private val odd = ByteArray(700) { (it % 251).toByte() }
    private val json = """{"assets": []}""".toByteArray()

    @Test
    fun readsTheNamedEntryExactly() {
        val archive = tar("strings/odd.bin" to odd, "entries.json" to json)
        assertArrayEquals(json, Tar.entry(ByteArrayInputStream(archive), "entries.json"))
        assertArrayEquals(odd, Tar.entry(ByteArrayInputStream(archive), "strings/odd.bin"))
    }

    @Test
    fun anAbsentEntryIsNull() {
        assertNull(Tar.entry(ByteArrayInputStream(tar("strings/odd.bin" to odd)), "entries.json"))
    }

    @Test(expected = IOException::class)
    fun anEntryOver16MbIsRefused() {
        Tar.entry(ByteArrayInputStream(header("entries.json", Tar.MAX_ENTRY_BYTES + 1L)), "entries.json")
    }
}

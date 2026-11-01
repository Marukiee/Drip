package nl.markmaaktmedia.drip.sources

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioTagsTest {

    private fun lyricsOf(bytes: ByteArray): String? {
        val f = File.createTempFile("drip", ".bin")
        try {
            f.writeBytes(bytes)
            return FileChannel.open(f.toPath(), StandardOpenOption.READ).use { AudioTags.lyrics(it) }
        } finally {
            f.delete()
        }
    }

    private fun le(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun be(v: Int) = ByteBuffer.allocate(4).putInt(v).array()

    private fun vorbis(vararg comments: String): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "test".toByteArray()
        out.write(le(vendor.size)); out.write(vendor)
        out.write(le(comments.size))
        comments.forEach { c -> val b = c.toByteArray(); out.write(le(b.size)); out.write(b) }
        return out.toByteArray()
    }

    @Test fun flacComments() {
        val comment = vorbis("TITLE=Song", "LYRICS=[00:01.00]one\n[00:02.00]two\n[00:03.00]three")
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        out.write(byteArrayOf(0, 0, 0, 34)); out.write(ByteArray(34))
        out.write(byteArrayOf(0x84.toByte(), 0, (comment.size shr 8).toByte(), comment.size.toByte())); out.write(comment)
        out.write(ByteArray(100))
        assertEquals("[00:01.00]one\n[00:02.00]two\n[00:03.00]three", lyricsOf(out.toByteArray()))
    }

    @Test fun id3Uslt() {
        val text = "First line\nSecond line"
        val body = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + text.toByteArray()
        val frame = "USLT".toByteArray() + be(body.size) + byteArrayOf(0, 0) + body
        val size = frame.size
        val header = "ID3".toByteArray() + byteArrayOf(3, 0, 0) +
            byteArrayOf(((size shr 21) and 0x7f).toByte(), ((size shr 14) and 0x7f).toByte(), ((size shr 7) and 0x7f).toByte(), (size and 0x7f).toByte())
        assertEquals(text, lyricsOf(header + frame + ByteArray(64)))
    }

    @Test fun id3Sylt() {
        val body = ByteArrayOutputStream()
        body.write(byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(2, 1, 0))
        body.write("Hello".toByteArray()); body.write(0); body.write(be(1500))
        body.write("World".toByteArray()); body.write(0); body.write(be(61_250))
        val b = body.toByteArray()
        val frame = "SYLT".toByteArray() + be(b.size) + byteArrayOf(0, 0) + b
        val size = frame.size
        val header = "ID3".toByteArray() + byteArrayOf(3, 0, 0, 0, 0, (size shr 7).toByte(), (size and 0x7f).toByte())
        assertEquals("[00:01.50]Hello\n[01:01.25]World\n", lyricsOf(header + frame))
    }

    private fun box(type: String, vararg children: ByteArray): ByteArray {
        val content = children.fold(ByteArray(0)) { a, b -> a + b }
        return be(8 + content.size) + type.toByteArray(Charsets.ISO_8859_1) + content
    }

    @Test fun mp4Lyrics() {
        val data = box("data", be(1) + be(0) + "Words here".toByteArray())
        val file = box("ftyp", "M4A ".toByteArray() + be(0)) +
            box("mdat", ByteArray(500)) +
            box("moov", box("udta", box("meta", ByteArray(4), box("ilst", box("©nam", box("data", be(1) + be(0) + "x".toByteArray())), box("©lyr", data)))))
        assertEquals("Words here", lyricsOf(file))
    }

    @Test fun nothingInOtherFiles() {
        assertNull(lyricsOf("RIFF....WAVEfmt ".toByteArray() + ByteArray(64)))
    }
}

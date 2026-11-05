package hr.bertlab.fluteassistant.debug.session

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a session's `audio.wav`: mono, 32-bit IEEE float (format tag 3), so a replay sees the
 * very samples the engine saw. The header's sizes are patched in [close]; a file a crash left
 * open reads as a header with zero samples. One `write` per hop, straight to the file: 8 KB
 * every 43 ms, nothing to buffer.
 */
class WavWriter(file: File, private val sampleRate: Int) : Closeable {
    private val out = RandomAccessFile(file, "rw")
    private var bytes = ByteBuffer.allocate(0)

    /** Samples written so far. */
    var frames = 0L
        private set

    init {
        out.setLength(0)
        out.write(header(0))
    }

    fun write(samples: FloatArray, count: Int) {
        if (bytes.capacity() < count * 4) bytes = ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.clear()
        for (i in 0 until count) bytes.putFloat(samples[i])
        out.write(bytes.array(), 0, count * 4)
        frames += count
    }

    override fun close() {
        out.seek(0)
        out.write(header(frames * 4))
        out.close()
    }

    private fun header(dataBytes: Long): ByteArray {
        val b = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt((36 + dataBytes).toInt()).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(FLOAT).putShort(1).putInt(sampleRate)
            .putInt(sampleRate * 4).putShort(4).putShort(32)
        b.put("data".toByteArray()).putInt(dataBytes.toInt())
        return b.array()
    }

    private companion object {
        const val HEADER = 44
        const val FLOAT: Short = 3
    }
}

/** Reads a mono WAV, the recorder's float one or a 16-bit PCM one another tool wrote (scaled to -1..1). */
object WavFile {
    class Audio(val samples: FloatArray, val sampleRate: Int)

    fun read(file: File): Audio {
        val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(b.remaining() >= 12 && tag(b) == "RIFF") { "not a RIFF file: $file" }
        b.int
        require(tag(b) == "WAVE") { "not a WAV file: $file" }
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        while (b.remaining() >= 8) {
            val id = tag(b)
            val size = b.int
            val start = b.position()
            when (id) {
                "fmt " -> {
                    format = b.short.toInt()
                    channels = b.short.toInt()
                    rate = b.int
                    b.int
                    b.short
                    bits = b.short.toInt()
                }
                "data" -> {
                    require(channels == 1) { "mono only, this file has $channels channels: $file" }
                    // A size of zero is a header never patched (the app died mid-run): the samples are all there, read to the end.
                    val n = if (size == 0) b.remaining() else minOf(size, b.remaining())
                    val samples = when {
                        format == 3 && bits == 32 -> FloatArray(n / 4) { b.float }
                        format == 1 && bits == 16 -> FloatArray(n / 2) { b.short / 32768f }
                        else -> throw IllegalArgumentException("format $format with $bits bits is not supported: $file")
                    }
                    return Audio(samples, rate)
                }
            }
            b.position(start + size + (size and 1))
        }
        throw IllegalArgumentException("no data chunk: $file")
    }

    private fun tag(b: ByteBuffer) = String(ByteArray(4).also { b.get(it) }, Charsets.US_ASCII)
}

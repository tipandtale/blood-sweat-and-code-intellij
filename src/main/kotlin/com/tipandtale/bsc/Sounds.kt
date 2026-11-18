package com.tipandtale.bsc

import com.intellij.openapi.application.ApplicationManager
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * The mp3 clips (src/main/resources/sounds), played as they are.
 * [gain] only evens out loudness between clips; the files themselves are untouched.
 */
enum class Clip(val file: String, val gain: Float) {
    JIMIN_LAUGH("jiminlaugh.mp3", 0.67f),
    JIMIN_SING("jiminsing.mp3", 0.32f),
    JUNGKOOK("jungkook.mp3", 0.40f),
    MIC_DROP("micdrop.mp3", 0.60f),
    CHICKEN_NOODLE_SOUP("chickennoodlesoup.mp3", 1.18f)
}

object Sounds {
    private const val TYPING_MIN_GAP_MS = 120L
    private const val DELETING_MIN_GAP_MS = 400L
    private const val MAX_AT_ONCE = 6

    private val cache = ConcurrentHashMap<Clip, ByteArray>()
    private val playing = AtomicInteger(0)

    @Volatile private var lastTypingAt = 0L
    @Volatile private var lastDeletingAt = 0L
    private var typingFlip = false
    private var deletingFlip = false

    /** Typing alternates Jimin laugh and Jimin sing. Throttled so held keys don't pile up. */
    fun typing() {
        val now = System.currentTimeMillis()
        if (now - lastTypingAt < TYPING_MIN_GAP_MS) return
        lastTypingAt = now
        typingFlip = !typingFlip
        play(if (typingFlip) Clip.JIMIN_LAUGH else Clip.JIMIN_SING)
    }

    /** Deleting alternates Jungkook and the mic drop. */
    fun deleting() {
        val now = System.currentTimeMillis()
        if (now - lastDeletingAt < DELETING_MIN_GAP_MS) return
        lastDeletingAt = now
        deletingFlip = !deletingFlip
        play(if (deletingFlip) Clip.JUNGKOOK else Clip.MIC_DROP)
    }

    fun play(clip: Clip) {
        if (playing.get() >= MAX_AT_ONCE) return
        val bytes = load(clip) ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            playing.incrementAndGet()
            try {
                decodeAndPlay(bytes, clip.gain)
            } catch (_: Throwable) {
                // No audio device, or the device is busy. Silence is fine.
            } finally {
                playing.decrementAndGet()
            }
        }
    }

    private fun load(clip: Clip): ByteArray? {
        cache[clip]?.let { return it }
        val bytes = Sounds::class.java.getResourceAsStream("/sounds/${clip.file}")?.use { it.readBytes() } ?: return null
        cache[clip] = bytes
        return bytes
    }

    /** Decodes the mp3 frame by frame and streams the samples (scaled by [gain]) to the sound card. */
    private fun decodeAndPlay(mp3: ByteArray, gain: Float) {
        val bitstream = Bitstream(ByteArrayInputStream(mp3))
        val decoder = Decoder()
        var line: SourceDataLine? = null
        try {
            while (true) {
                val header = bitstream.readFrame() ?: break
                val out = decoder.decodeFrame(header, bitstream) as SampleBuffer
                var l = line
                if (l == null) {
                    val format = AudioFormat(out.sampleFrequency.toFloat(), 16, out.channelCount, true, false)
                    l = AudioSystem.getSourceDataLine(format)
                    l.open(format)
                    l.start()
                    line = l
                }
                val samples = out.buffer
                val count = out.bufferLength
                val bytes = ByteArray(count * 2)
                for (i in 0 until count) {
                    val v = (samples[i] * gain).toInt().coerceIn(-32768, 32767)
                    bytes[i * 2] = (v and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                l.write(bytes, 0, bytes.size)
                bitstream.closeFrame()
            }
            line?.drain()
        } finally {
            line?.close()
            bitstream.close()
        }
    }
}

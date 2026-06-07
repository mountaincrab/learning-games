package com.mountaincrab.learninggames.game.shapegame

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

private const val SAMPLE_RATE = 44_100

@Composable
actual fun rememberGameAudio(): GameAudio {
    val audio = remember { AndroidGameAudio() }
    DisposableEffect(Unit) {
        onDispose { audio.release() }
    }
    return audio
}

/**
 * Synthesises all audio with [AudioTrack] — no asset files. The chime is a sparkly
 * ascending arpeggio with bell-like decay; the music is a short, gentle pentatonic
 * loop with a soft rhythmic pulse.
 */
private class AndroidGameAudio : GameAudio {
    // Cached pool so overlapping chimes can ring together without blocking.
    private val sfx = Executors.newCachedThreadPool()
    private val musicExec = Executors.newSingleThreadExecutor()

    private val chimeData by lazy { synthChime() }
    private val musicData by lazy { synthMusicLoop() }

    @Volatile private var released = false
    @Volatile private var musicTrack: AudioTrack? = null

    override fun playMagicChime() {
        if (released) return
        sfx.execute {
            val track = newStaticTrack(chimeData, loop = false, volume = 0.55f)
            track.play()
            // Let it ring out, then free the track.
            Thread.sleep(chimeData.size * 1000L / SAMPLE_RATE + 120)
            runCatching { track.release() }
        }
    }

    override fun startMusic() {
        if (released) return
        musicExec.execute {
            if (musicTrack != null || released) return@execute
            val track = newStaticTrack(musicData, loop = true, volume = 0.26f)
            track.play()
            musicTrack = track
        }
    }

    override fun stopMusic() {
        musicExec.execute {
            musicTrack?.let { t ->
                runCatching { t.stop() }
                runCatching { t.release() }
            }
            musicTrack = null
        }
    }

    fun release() {
        released = true
        stopMusic()
        musicExec.shutdown()
        sfx.shutdown()
    }

    private fun newStaticTrack(data: ShortArray, loop: Boolean, volume: Float): AudioTrack {
        val bytes = data.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, data.size)
        track.setVolume(volume)
        if (loop) track.setLoopPoints(0, data.size, -1)
        return track
    }
}

// --- Synthesis ----------------------------------------------------------------

private fun floatToPcm(f: FloatArray): ShortArray {
    val out = ShortArray(f.size)
    for (i in f.indices) {
        val v = (f[i] * Short.MAX_VALUE).coerceIn(-32_767f, 32_767f)
        out[i] = v.toInt().toShort()
    }
    return out
}

/** A bell tone: near-instant attack, exponential decay. */
private fun addBell(buf: FloatArray, startSec: Float, freq: Float, amp: Float, decay: Float, dur: Float) {
    val start = (startSec * SAMPLE_RATE).toInt()
    val n = (dur * SAMPLE_RATE).toInt()
    for (i in 0 until n) {
        val idx = start + i
        if (idx < 0) continue
        if (idx >= buf.size) break
        val t = i.toFloat() / SAMPLE_RATE
        val env = exp(-t / decay) * (1f - exp(-t / 0.004f))
        buf[idx] += sin(2.0 * PI * freq * t).toFloat() * amp * env
    }
}

/** A soft sine note with smooth attack/release (no clicks at the loop seam). */
private fun addSoft(buf: FloatArray, startSec: Float, freq: Float, amp: Float, dur: Float, decay: Float = 0.25f) {
    val start = (startSec * SAMPLE_RATE).toInt()
    val n = (dur * SAMPLE_RATE).toInt()
    val attack = 0.02f
    val release = 0.06f
    for (i in 0 until n) {
        val idx = start + i
        if (idx < 0) continue
        if (idx >= buf.size) break
        val t = i.toFloat() / SAMPLE_RATE
        val a = if (t < attack) t / attack else 1f
        val d = exp(-t / decay)
        val r = if (t > dur - release) ((dur - t) / release).coerceIn(0f, 1f) else 1f
        buf[idx] += sin(2.0 * PI * freq * t).toFloat() * amp * a * d * r
    }
}

private fun synthChime(): ShortArray {
    val dur = 1.4f
    val buf = FloatArray((dur * SAMPLE_RATE).toInt())
    // Ascending high pentatonic arpeggio + an octave shimmer on top.
    val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 987.77f, 1174.66f, 1567.98f)
    for ((i, f) in notes.withIndex()) {
        addBell(buf, startSec = i * 0.07f, freq = f, amp = 0.15f, decay = 0.5f, dur = 1.0f)
        addBell(buf, startSec = i * 0.07f, freq = f * 2f, amp = 0.05f, decay = 0.35f, dur = 0.7f)
    }
    return floatToPcm(buf)
}

private fun synthMusicLoop(): ShortArray {
    val beat = 0.5f
    val beats = 8
    val buf = FloatArray((beat * beats * SAMPLE_RATE).toInt())

    // Gentle C-major-pentatonic melody.
    val c5 = 523.25f; val d5 = 587.33f; val e5 = 659.25f; val g5 = 783.99f; val a5 = 880.0f
    val melody = floatArrayOf(e5, g5, a5, g5, e5, d5, c5, d5)
    for ((i, f) in melody.withIndex()) {
        addSoft(buf, i * beat, f, amp = 0.13f, dur = beat * 0.9f)
    }

    // Soft bass pulse on beats 1 and 5 for a calm rhythm.
    addSoft(buf, 0f, 130.81f, amp = 0.16f, dur = beat * 1.6f, decay = 0.5f)        // C3
    addSoft(buf, 4 * beat, 196.0f, amp = 0.16f, dur = beat * 1.6f, decay = 0.5f)   // G3

    // A whisper-quiet off-beat tick keeps things gently rhythmic.
    for (b in 0 until beats) {
        addSoft(buf, b * beat + beat * 0.5f, 2200f, amp = 0.025f, dur = 0.05f, decay = 0.03f)
    }
    return floatToPcm(buf)
}

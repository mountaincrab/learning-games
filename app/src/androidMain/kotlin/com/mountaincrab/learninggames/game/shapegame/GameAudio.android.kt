package com.mountaincrab.learninggames.game.shapegame

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberGameAudio(): GameAudio {
    val context = LocalContext.current
    val audio = remember { AndroidGameAudio(context) }
    DisposableEffect(Unit) {
        onDispose { audio.release() }
    }
    return audio
}

/**
 * Plays bundled OGG audio shipped in `res/raw`:
 *  - `magic_chime.ogg`      — short sound effect on each match (via [SoundPool])
 *  - `background_music.ogg` — gentle looping music (via [MediaPlayer])
 *
 * Resources are resolved by name, so the app still builds and simply stays quiet
 * if a file hasn't been added yet.
 */
private class AndroidGameAudio(context: Context) : GameAudio {
    private val appContext = context.applicationContext

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    @Volatile private var chimeId = 0
    @Volatile private var chimeReady = false
    private var music: MediaPlayer? = null

    init {
        val res = rawId("magic_chime")
        if (res != 0) {
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (sampleId == chimeId && status == 0) chimeReady = true
            }
            chimeId = soundPool.load(appContext, res, 1)
        }
    }

    override fun playMagicChime() {
        if (chimeReady) soundPool.play(chimeId, 0.7f, 0.7f, 1, 0, 1f)
    }

    override fun startMusic() {
        if (music != null) return
        val res = rawId("background_music")
        if (res == 0) return
        music = MediaPlayer.create(appContext, res)?.apply {
            isLooping = true
            setVolume(0.35f, 0.35f)
            start()
        }
    }

    override fun stopMusic() {
        music?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            mp.release()
        }
        music = null
    }

    fun release() {
        stopMusic()
        soundPool.release()
    }

    /** Resource id of a `res/raw/<name>` file, or 0 if it isn't bundled. */
    private fun rawId(name: String): Int =
        appContext.resources.getIdentifier(name, "raw", appContext.packageName)
}

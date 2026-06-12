package com.mountaincrab.learninggames.game.trumpergame

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.random.Random

@Composable
actual fun rememberTrumperAudio(): TrumperAudio {
    val context = LocalContext.current
    val audio = remember { AndroidTrumperAudio(context) }
    DisposableEffect(Unit) {
        onDispose { audio.release() }
    }
    return audio
}

/**
 * Plays bundled fart OGGs shipped in `res/raw` as `fart_1.ogg`, `fart_2.ogg`, … (up
 * to [MAX_FARTS]) and the four opening-sequence name shouts (`name_penny.ogg`, …).
 * All present files are loaded into a [SoundPool]; [playFart] picks a random ready
 * one and [playName] plays a specific character's clip. Resources are resolved by
 * name, so the app still builds and simply stays quiet if no files have been added.
 */
private class AndroidTrumperAudio(context: Context) : TrumperAudio {
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

    /** Sample ids that have finished loading and are safe to play. */
    private val ready = mutableSetOf<Int>()

    /** SoundPool sample id per character (column order), or 0 if its clip is absent. */
    private val nameIds = IntArray(NAME_FILES.size)

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(ready) { ready += sampleId }
        }
        for (i in 1..MAX_FARTS) {
            val res = rawId("fart_$i")
            if (res != 0) soundPool.load(appContext, res, 1)
        }
        NAME_FILES.forEachIndexed { i, file ->
            val res = rawId("name_$file")
            nameIds[i] = if (res != 0) soundPool.load(appContext, res, 1) else 0
        }
    }

    override fun playFart() {
        val id = synchronized(ready) { ready.toList() }
            .takeIf { it.isNotEmpty() }
            ?.let { it[Random.nextInt(it.size)] }
            ?: return
        soundPool.play(id, 0.9f, 0.9f, 1, 0, 1f)
    }

    override fun playName(id: Int) {
        val sampleId = nameIds.getOrElse(id) { 0 }
        if (sampleId == 0) return
        if (synchronized(ready) { sampleId !in ready }) return
        soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }

    /** Resource id of a `res/raw/<name>` file, or 0 if it isn't bundled. */
    private fun rawId(name: String): Int =
        appContext.resources.getIdentifier(name, "raw", appContext.packageName)

    private companion object {
        const val MAX_FARTS = 12

        /** Name-clip base filenames in column order (`res/raw/name_<file>.ogg`). */
        val NAME_FILES = listOf("penny", "taz", "zach", "rory")
    }
}

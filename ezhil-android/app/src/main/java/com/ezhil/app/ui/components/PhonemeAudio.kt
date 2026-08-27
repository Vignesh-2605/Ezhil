package com.ezhil.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

/**
 * Plays bundled phoneme recordings from `assets/sounds/`.
 *
 * The phonics games use a small, fixed set of Tamil sounds. Driving those
 * through the system TTS engine would make a classroom download a ~100 MB
 * voice pack to hear three letters — unworkable on budget devices with no
 * data. Recordings ship with the APK, are a few KB each, need no setup, and a
 * native human voice teaches letter sounds better than a synthetic one.
 *
 * Falls back to [TamilTtsState] only when a clip is missing, so lessons with
 * unbounded generated vocabulary still speak where a voice is installed.
 */
class PhonemeAudioState(private val context: Context) {

    private var player: MediaPlayer? = null

    /** True while a clip is sounding. Callers that would start another clip —
     *  the next round's prompt, say — should wait, or the first is cut off
     *  mid-word. Compose state so recomposition can react to it. */
    var isPlaying by mutableStateOf(false)
        private set

    /** Suspend until nothing is playing, so one clip never truncates another. */
    suspend fun awaitIdle(timeoutMs: Long = 4_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (isPlaying && System.currentTimeMillis() < deadline) delay(40)
    }

    /** Asset filenames present under sounds/, resolved once at construction. */
    private val available: Set<String> = runCatching {
        context.assets.list(ASSET_DIR)?.toSet().orEmpty()
    }.getOrElse {
        Log.w(TAG, "could not list $ASSET_DIR", it)
        emptySet()
    }

    fun hasClip(phoneme: String): Boolean = resolve(phoneme) != null

    /** First bundled file matching this phoneme, whatever the container.
     *  Keeping this format-agnostic means replacing a synthesised clip with a
     *  human recording is a file drop, not a code change. */
    private fun resolve(phoneme: String): String? {
        val stem = stemFor(phoneme)
        return EXTENSIONS.firstNotNullOfOrNull { ext ->
            "$stem$ext".takeIf { it in available }
        }
    }

    /**
     * Play [phoneme]'s recording.
     * @return true when a clip existed and playback started.
     */
    fun play(phoneme: String): Boolean {
        val name = resolve(phoneme) ?: return false

        return runCatching {
            release()
            player = MediaPlayer().apply {
                // USAGE_MEDIA, not USAGE_ASSISTANCE_SONIFICATION. The latter
                // routes to STREAM_SYSTEM, which Android mutes whenever the
                // phone is on vibrate or silent — MediaPlayer still starts and
                // AudioTrack still opens, so the logs look healthy while the
                // child hears nothing. Most phones live on vibrate.
                // STREAM_MUSIC also means the volume keys adjust it during
                // playback, which is what a teacher will reach for.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                context.assets.openFd("$ASSET_DIR/$name").use { fd ->
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                setOnCompletionListener { release() }
                prepare()
                start()
            }
            isPlaying = true
            true
        }.getOrElse {
            Log.w(TAG, "failed to play $name", it)
            false
        }
    }

    internal fun release() {
        runCatching { player?.release() }
        player = null
        isPlaying = false
    }

    private companion object {
        const val TAG = "EzhilPhonemeAudio"
        const val ASSET_DIR = "sounds"

        /** Checked in order, so a hand-recorded .wav wins over a bundled .mp3. */
        val EXTENSIONS = listOf(".wav", ".m4a", ".ogg", ".mp3")

        /** Unicode code points, so filenames stay ASCII and survive any
         *  build tool or filesystem that mangles Tamil characters. */
        fun stemFor(phoneme: String): String =
            phoneme.trim().map { it.code.toString(16) }.joinToString("_")
    }
}

@Composable
fun rememberPhonemeAudio(): PhonemeAudioState {
    val context = LocalContext.current
    val state = remember { PhonemeAudioState(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { state.release() } }
    return state
}

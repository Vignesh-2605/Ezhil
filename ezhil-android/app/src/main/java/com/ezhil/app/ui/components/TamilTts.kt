package com.ezhil.app.ui.components

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Shared Tamil text-to-speech holder.
 *
 * Budget Android devices frequently ship without a Tamil voice. Callers MUST
 * check [available] and hide/disable their audio controls when it is false —
 * speaking Tamil text through a missing voice either fails silently or reads
 * it with English phonetics, which is worse than no audio for a child
 * learning to read. [openVoiceInstaller] deep-links to the system TTS data
 * installer so a teacher can add the voice once per device.
 */
class TamilTtsState {
    var available by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null

    internal fun init(context: Context) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val primary = tts?.setLanguage(Locale("ta", "IN"))
            available = when (primary) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                    val fallback = tts?.setLanguage(Locale("ta"))
                    fallback != TextToSpeech.LANG_MISSING_DATA &&
                        fallback != TextToSpeech.LANG_NOT_SUPPORTED
                }
                null -> false
                else -> true
            }
        }
    }

    fun speak(text: String, utteranceId: String = "ezhil_tts") {
        if (available) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun openVoiceInstaller(context: Context) {
        runCatching {
            context.startActivity(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    internal fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        available = false
    }
}

@Composable
fun rememberTamilTts(): TamilTtsState {
    val context = LocalContext.current
    val state = remember { TamilTtsState() }
    DisposableEffect(Unit) {
        state.init(context)
        onDispose { state.shutdown() }
    }
    return state
}

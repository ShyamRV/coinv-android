package com.coinv.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

class VoiceSpeaker(
    context: Context,
    private val onReady: () -> Unit
) {
    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                onReady()
            }
        }
    }

    fun speak(text: String, onDone: () -> Unit) {
        val engine = tts ?: run {
            mainHandler.post { onDone() }
            return
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                mainHandler.post { onDone() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { onDone() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { onDone() }
            }
        })

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val UTTERANCE_ID = "coinv_utterance"
    }
}

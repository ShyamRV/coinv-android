package com.coinv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.coinv.app.data.settings.SettingsRepository
import com.coinv.app.ui.CoinVApp
import com.coinv.app.ui.theme.CoinVTheme
import com.coinv.app.voice.VoiceListener
import com.coinv.app.voice.VoiceSpeaker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var voiceListener: VoiceListener
    private lateinit var voiceSpeaker: VoiceSpeaker

    private var speechResultHandler: ((String) -> Unit)? = null
    private var speechErrorHandler: ((String) -> Unit)? = null
    private var partialResultHandler: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        voiceListener = VoiceListener(
            context = this,
            onResult = { text -> speechResultHandler?.invoke(text) },
            onError = { message -> speechErrorHandler?.invoke(message) },
            onPartial = { partial -> partialResultHandler?.invoke(partial) }
        )
        voiceSpeaker = VoiceSpeaker(context = this, onReady = {})

        setContent {
            val settings by settingsRepository.settings.collectAsState(
                initial = com.coinv.app.data.settings.AppSettings()
            )
            CoinVTheme(darkTheme = settings.theme != "light") {
                CoinVApp(
                    voiceListener = voiceListener,
                    voiceSpeaker = voiceSpeaker,
                    onSpeechResult = { handler -> speechResultHandler = handler },
                    onSpeechError = { handler -> speechErrorHandler = handler },
                    onPartialResult = { handler -> partialResultHandler = handler }
                )
            }
        }
    }

    override fun onDestroy() {
        voiceListener.destroy()
        voiceSpeaker.shutdown()
        super.onDestroy()
    }
}

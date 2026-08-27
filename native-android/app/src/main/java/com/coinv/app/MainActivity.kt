package com.coinv.app

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.coinv.app.di.HeadsetEntryPoint
import com.coinv.app.ui.CoinVApp
import com.coinv.app.ui.theme.CoinVTheme
import com.coinv.app.voice.HeadsetMediaController
import com.coinv.app.voice.VoiceListener
import com.coinv.app.voice.VoiceSpeaker
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var voiceListener: VoiceListener? = null
    private var voiceSpeaker: VoiceSpeaker? = null
    private var headsetMediaController: HeadsetMediaController? = null

    private var speechResultHandler: ((String) -> Unit)? = null
    private var speechErrorHandler: ((String) -> Unit)? = null
    private var partialResultHandler: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LaunchProbe.mark(this, "main_onCreate")

        // Native first frame — proves Activity starts even if Compose/Hilt later fails.
        val boot = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#08090C"))
            addView(
                TextView(this@MainActivity).apply {
                    text = "CoinV"
                    setTextColor(Color.parseColor("#E8EEF8"))
                    textSize = 22f
                    gravity = Gravity.CENTER
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(boot)

        try {
            enableEdgeToEdge()
        } catch (t: Throwable) {
            Log.w("MainActivity", "enableEdgeToEdge failed", t)
        }

        // Defer heavy work until after native window is shown.
        window.decorView.post {
            LaunchProbe.mark(this, "main_post_frame")
            try {
                voiceListener = VoiceListener(
                    context = this,
                    onResult = { text -> speechResultHandler?.invoke(text) },
                    onError = { message -> speechErrorHandler?.invoke(message) },
                    onPartial = { partial -> partialResultHandler?.invoke(partial) }
                )
                voiceSpeaker = VoiceSpeaker(context = this, onReady = {})
                LaunchProbe.mark(this, "voice_ok")

                val listener = voiceListener!!
                val speaker = voiceSpeaker!!
                setContent {
                    CoinVTheme(darkTheme = true) {
                        CoinVApp(
                            voiceListener = listener,
                            voiceSpeaker = speaker,
                            onSpeechResult = { handler -> speechResultHandler = handler },
                            onSpeechError = { handler -> speechErrorHandler = handler },
                            onPartialResult = { handler -> partialResultHandler = handler }
                        )
                    }
                }
                LaunchProbe.mark(this, "compose_set")
            } catch (t: Throwable) {
                CrashLogger.persist(this, t)
                LaunchProbe.mark(this, "compose_fail:${t.javaClass.simpleName}")
                (boot.getChildAt(0) as? TextView)?.text =
                    "CoinV failed to start.\nSee Android/data/com.coinv.app/files/coinv-last-crash.txt"
            }

            window.decorView.post {
                try {
                    headsetMediaController = EntryPointAccessors.fromApplication(
                        applicationContext,
                        HeadsetEntryPoint::class.java
                    ).headsetMediaController()
                    headsetMediaController?.activate()
                    LaunchProbe.mark(this, "headset_ok")
                } catch (t: Throwable) {
                    CrashLogger.persist(this, t)
                    LaunchProbe.mark(this, "headset_fail:${t.javaClass.simpleName}")
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return try {
            if (headsetMediaController?.handleKeyEvent(event) == true) true
            else super.dispatchKeyEvent(event)
        } catch (t: Throwable) {
            CrashLogger.persist(this, t)
            super.dispatchKeyEvent(event)
        }
    }

    override fun onDestroy() {
        try {
            voiceListener?.destroy()
            voiceSpeaker?.shutdown()
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}

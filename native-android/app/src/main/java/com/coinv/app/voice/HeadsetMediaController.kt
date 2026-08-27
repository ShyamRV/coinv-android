package com.coinv.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import com.coinv.app.domain.HeadsetAction
import com.coinv.app.domain.HeadsetModeDispatcher
import com.coinv.app.domain.HeadsetTapDetector
import com.coinv.app.ui.HapticFeedback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeadsetMediaController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tapDetector: HeadsetTapDetector,
    private val headsetModeDispatcher: HeadsetModeDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingTapJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: android.content.Intent): Boolean {
            val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(android.content.Intent.EXTRA_KEY_EVENT)
            return keyEvent?.let { handleKeyEvent(it) } ?: super.onMediaButtonEvent(mediaButtonEvent)
        }

        override fun onPlay() {
            onHeadsetButton()
        }

        override fun onPause() {
            onHeadsetButton()
        }
    }

    fun sessionToken(): MediaSessionCompat.Token = ensureSession().sessionToken

    fun activate() {
        mainHandler.post {
            try {
                val session = ensureSession()
                session.isActive = true
                requestAudioFocus()
            } catch (e: Exception) {
                Log.e(TAG, "activate failed", e)
            }
        }
    }

    fun release() {
        pendingTapJob?.cancel()
        abandonAudioFocus()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (e: Exception) {
            Log.w(TAG, "release failed: ${e.message}")
        } finally {
            mediaSession = null
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                onHeadsetButton()
                true
            }
            else -> false
        }
    }

    fun onHeadsetButton() {
        val now = System.currentTimeMillis()
        val immediate = tapDetector.onMediaButtonPress(now)
        when (immediate) {
            HeadsetAction.DoubleTap -> {
                pendingTapJob?.cancel()
                tapDetector.reset()
                HapticFeedback.pulse(context, pulses = 2)
                headsetModeDispatcher.dispatchDoubleTap()
            }
            else -> {
                pendingTapJob?.cancel()
                pendingTapJob = scope.launch {
                    delay(HeadsetTapDetector.SINGLE_TAP_CONFIRM_MS)
                    when (tapDetector.flushPendingSingleTap()) {
                        HeadsetAction.SingleTap -> {
                            HapticFeedback.pulse(context, pulses = 1)
                            headsetModeDispatcher.dispatchSingleTap()
                        }
                        HeadsetAction.DoubleTap -> {
                            HapticFeedback.pulse(context, pulses = 2)
                            headsetModeDispatcher.dispatchDoubleTap()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun ensureSession(): MediaSessionCompat {
        mediaSession?.let { return it }
        return MediaSessionCompat(context, "CoinVVoice").also { session ->
            session.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            session.setCallback(sessionCallback, mainHandler)
            session.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
                    .build()
            )
            mediaSession = session
        }
    }

    private fun requestAudioFocus() {
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } catch (e: Exception) {
            Log.w(TAG, "requestAudioFocus failed: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (e: Exception) {
            Log.w(TAG, "abandonAudioFocus failed: ${e.message}")
        } finally {
            focusRequest = null
        }
    }

    companion object {
        private const val TAG = "HeadsetMedia"
    }
}

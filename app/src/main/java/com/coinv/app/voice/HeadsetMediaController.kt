package com.coinv.app.voice

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import com.coinv.app.ui.HapticFeedback
import com.coinv.app.domain.HeadsetAction
import com.coinv.app.domain.HeadsetTapDetector
import com.coinv.app.domain.ModeController
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
    private val modeController: ModeController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingTapJob: Job? = null

    private val mediaSession: MediaSessionCompat = MediaSessionCompat(context, "CoinVVoice").apply {
        setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        setCallback(sessionCallback)
        isActive = true
        setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
                .build()
        )
    }

    fun activate() {
        mediaSession.isActive = true
    }

    fun release() {
        pendingTapJob?.cancel()
        mediaSession.isActive = false
        mediaSession.release()
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
                modeController.handleHeadsetDoubleTap()
            }
            else -> {
                pendingTapJob?.cancel()
                pendingTapJob = scope.launch {
                    delay(HeadsetTapDetector.SINGLE_TAP_CONFIRM_MS)
                    when (tapDetector.flushPendingSingleTap()) {
                        HeadsetAction.SingleTap -> {
                            HapticFeedback.pulse(context, pulses = 1)
                            modeController.handleHeadsetSingleTap()
                        }
                        HeadsetAction.DoubleTap -> {
                            HapticFeedback.pulse(context, pulses = 2)
                            modeController.handleHeadsetDoubleTap()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

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
}

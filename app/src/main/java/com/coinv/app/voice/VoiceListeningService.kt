package com.coinv.app.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.coinv.app.MainActivity
import com.coinv.app.domain.AppMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VoiceListeningService : Service() {

    @Inject lateinit var headsetMediaController: HeadsetMediaController

    private var mediaSession: MediaSessionCompat? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        headsetMediaController.activate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val mode = intent?.getStringExtra(EXTRA_MODE) ?: AppMode.Listening.name.lowercase()
                createChannel()
                startForeground(NOTIFICATION_ID, buildNotification(mode))
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun buildNotification(mode: String): Notification {
        val label = when (mode) {
            AppMode.Monitoring.name.lowercase() -> "Monitoring — gathering context silently"
            AppMode.Listening.name.lowercase() -> "Listening — tap headset to pause"
            else -> "CoinV active"
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceListeningService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val session = ensureMediaSession()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoinV · ${mode.replaceFirstChar { it.uppercase() }}")
            .setContentText(label)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun ensureMediaSession(): MediaSessionCompat {
        mediaSession?.let { return it }
        return MediaSessionCompat(this, "CoinVServiceSession").also { session ->
            session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)
            session.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            session.isActive = true
            mediaSession = session
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CoinV Voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when CoinV is listening or monitoring"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.coinv.app.action.START_LISTENING"
        const val ACTION_STOP = "com.coinv.app.action.STOP_LISTENING"
        const val EXTRA_MODE = "app_mode"
        private const val CHANNEL_ID = "coinv_voice"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, mode: String) {
            val intent = Intent(context, VoiceListeningService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceListeningService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

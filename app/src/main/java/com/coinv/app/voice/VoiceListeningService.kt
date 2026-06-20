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
import androidx.core.app.NotificationCompat
import com.coinv.app.MainActivity

class VoiceListeningService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val mode = intent?.getStringExtra(EXTRA_MODE) ?: "always_listening"
                createChannel()
                startForeground(NOTIFICATION_ID, buildNotification(mode))
                return START_STICKY
            }
        }
    }

    private fun buildNotification(mode: String): Notification {
        val label = when (mode) {
            "wake_word" -> "Wake word listening active"
            "always_listening" -> "Continuous listening active"
            else -> "Microphone active"
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoinV")
            .setContentText(label)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CoinV Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when CoinV is using the microphone"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.coinv.app.action.START_LISTENING"
        const val ACTION_STOP = "com.coinv.app.action.STOP_LISTENING"
        const val EXTRA_MODE = "listening_mode"
        private const val CHANNEL_ID = "coinv_listening"
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

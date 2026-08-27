package com.coinv.app.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.coinv.app.di.HeadsetEntryPoint
import dagger.hilt.android.EntryPointAccessors

class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON != intent.action) return
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        val controller = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HeadsetEntryPoint::class.java
        ).headsetMediaController()
        controller.handleKeyEvent(event)
    }
}

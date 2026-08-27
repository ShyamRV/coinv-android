package com.coinv.app.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge so headset taps reach VoiceViewModel.enterListening / enterMonitoring / enterIdle
 * when the activity is alive. Falls back to [ModeController] (same enter* + FGS) if unbound.
 */
interface HeadsetModeActions {
    fun onHeadsetSingleTap()
    fun onHeadsetDoubleTap()
}

@Singleton
class HeadsetModeDispatcher @Inject constructor(
    private val modeController: ModeController
) {
    @Volatile
    private var actions: HeadsetModeActions? = null

    fun bind(actions: HeadsetModeActions) {
        this.actions = actions
    }

    fun unbind(actions: HeadsetModeActions) {
        if (this.actions === actions) this.actions = null
    }

    fun dispatchSingleTap() {
        actions?.onHeadsetSingleTap() ?: modeController.handleHeadsetSingleTap()
    }

    fun dispatchDoubleTap() {
        actions?.onHeadsetDoubleTap() ?: modeController.handleHeadsetDoubleTap()
    }
}

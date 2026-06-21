package com.coinv.app.domain

import javax.inject.Inject
import javax.inject.Singleton

enum class HeadsetAction {
    SingleTap,
    DoubleTap,
    Ignored
}

@Singleton
class HeadsetTapDetector @Inject constructor() {

    private var lastTapMs = 0L
    private var tapCount = 0
    private var debounceJobMs = 0L

    fun onMediaButtonPress(nowMs: Long = System.currentTimeMillis()): HeadsetAction {
        val elapsed = nowMs - lastTapMs
        if (elapsed > DOUBLE_TAP_WINDOW_MS) {
            tapCount = 1
        } else {
            tapCount++
        }
        lastTapMs = nowMs
        debounceJobMs = nowMs

        return when {
            tapCount >= 2 -> {
                tapCount = 0
                HeadsetAction.DoubleTap
            }
            tapCount == 1 && elapsed > DOUBLE_TAP_WINDOW_MS -> {
                // First tap — wait briefly; caller should confirm no second tap via flushPendingTap
                HeadsetAction.Ignored
            }
            else -> HeadsetAction.Ignored
        }
    }

    fun flushPendingSingleTap(nowMs: Long = System.currentTimeMillis()): HeadsetAction? {
        if (tapCount == 1 && nowMs - lastTapMs >= SINGLE_TAP_CONFIRM_MS) {
            tapCount = 0
            return HeadsetAction.SingleTap
        }
        if (tapCount >= 2) {
            tapCount = 0
            return HeadsetAction.DoubleTap
        }
        return null
    }

    fun reset() {
        tapCount = 0
        lastTapMs = 0L
    }

    companion object {
        const val DOUBLE_TAP_WINDOW_MS = 450L
        const val SINGLE_TAP_CONFIRM_MS = 320L
    }
}

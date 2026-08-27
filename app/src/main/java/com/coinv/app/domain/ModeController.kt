package com.coinv.app.domain

import android.content.Context
import android.util.Log
import com.coinv.app.data.local.dao.ModeHistoryDao
import com.coinv.app.data.local.entity.ModeHistoryEntity
import com.coinv.app.data.settings.SettingsRepository
import com.coinv.app.voice.VoiceListeningService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val modeHistoryDao: ModeHistoryDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ModeState())
    val state: StateFlow<ModeState> = _state.asStateFlow()

    init {
        // Always cold-start Idle. Restoring Listening/Monitoring used to auto-start
        // SpeechRecognizer / FGS on first frame and could kill the process on some devices.
        scope.launch {
            try {
                settingsRepository.setCurrentAppMode("idle")
            } catch (e: Exception) {
                Log.w(TAG, "reset mode to idle failed: ${e.message}")
            }
        }
    }

    /**
     * Single mode-entry path: updates state and starts/stops [VoiceListeningService].
     * UI (VoiceViewModel) and headset gestures both end here — do not start the FGS elsewhere.
     */
    fun enterListening(source: String) {
        if (_state.value.mode == AppMode.Listening && _state.value.phase == VoicePhase.Capturing) return
        transition(AppMode.Listening, VoicePhase.Capturing, source)
        startListeningServiceSafe(AppMode.Listening.name.lowercase())
    }

    fun enterMonitoring(source: String) {
        scope.launch {
            if (!settingsRepository.settings.first().monitoringEnabled) return@launch
            if (_state.value.mode == AppMode.Monitoring) return@launch
            transition(AppMode.Monitoring, VoicePhase.Capturing, source)
            startListeningServiceSafe(AppMode.Monitoring.name.lowercase())
        }
    }

    fun enterIdle(source: String) {
        transition(AppMode.Idle, VoicePhase.None, source)
        try {
            VoiceListeningService.stop(context)
        } catch (e: Exception) {
            Log.w(TAG, "stopListeningService failed: ${e.message}")
        }
    }

    private fun startListeningServiceSafe(mode: String) {
        try {
            VoiceListeningService.start(context, mode)
        } catch (e: Exception) {
            // Missing mic permission / FGS restrictions must not crash the UI.
            Log.e(TAG, "startListeningService failed for mode=$mode: ${e.message}", e)
        }
    }

    fun setPhase(phase: VoicePhase, errorMessage: String? = null) {
        _state.value = _state.value.copy(phase = phase, errorMessage = errorMessage)
    }

    fun clearError() {
        _state.value = _state.value.copy(
            phase = if (_state.value.mode == AppMode.Idle) VoicePhase.None else VoicePhase.Capturing,
            errorMessage = null
        )
    }

    fun toggleFromOrb(currentHasMic: Boolean) {
        when (_state.value.mode) {
            AppMode.Idle -> enterListening("orb")
            AppMode.Listening -> if (currentHasMic) enterIdle("orb") else enterListening("orb")
            AppMode.Monitoring -> enterIdle("orb")
        }
    }

    /** Fallback when VoiceViewModel is not bound (activity destroyed, FGS still alive). */
    fun handleHeadsetSingleTap() {
        when (_state.value.mode) {
            AppMode.Monitoring -> enterListening("headset_single")
            AppMode.Listening -> enterIdle("headset_single")
            AppMode.Idle -> enterListening("headset_single")
        }
    }

    fun handleHeadsetDoubleTap() {
        when (_state.value.mode) {
            AppMode.Monitoring -> enterIdle("headset_double")
            else -> enterMonitoring("headset_double")
        }
    }

    private fun transition(mode: AppMode, phase: VoicePhase, source: String) {
        _state.value = ModeState(mode = mode, phase = phase, activatedBy = source)
        scope.launch {
            settingsRepository.setCurrentAppMode(mode.name.lowercase())
            modeHistoryDao.insert(
                ModeHistoryEntity(
                    mode = mode.name.lowercase(),
                    source = source,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    companion object {
        private const val TAG = "ModeController"
    }
}

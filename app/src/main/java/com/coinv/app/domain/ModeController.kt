package com.coinv.app.domain

import com.coinv.app.data.local.dao.ModeHistoryDao
import com.coinv.app.data.local.entity.ModeHistoryEntity
import com.coinv.app.data.settings.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
    private val modeHistoryDao: ModeHistoryDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ModeState())
    val state: StateFlow<ModeState> = _state.asStateFlow()

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                val restored = when (settings.currentAppMode) {
                    "listening" -> AppMode.Listening
                    "monitoring" -> AppMode.Monitoring
                    else -> AppMode.Idle
                }
                if (_state.value.mode == AppMode.Idle && restored != AppMode.Idle) {
                    _state.value = ModeState(mode = restored, activatedBy = "restored")
                }
            }
        }
    }

    fun enterListening(source: String) {
        if (_state.value.mode == AppMode.Listening && _state.value.phase == VoicePhase.Capturing) return
        transition(AppMode.Listening, VoicePhase.Capturing, source)
    }

    fun enterMonitoring(source: String) {
        if (_state.value.mode == AppMode.Monitoring) return
        transition(AppMode.Monitoring, VoicePhase.Capturing, source)
    }

    fun enterIdle(source: String) {
        transition(AppMode.Idle, VoicePhase.None, source)
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

    fun handleHeadsetSingleTap() {
        when (_state.value.mode) {
            AppMode.Monitoring -> enterListening("headset_single")
            AppMode.Listening -> enterIdle("headset_single")
            AppMode.Idle -> enterListening("headset_single")
        }
    }

    fun handleHeadsetDoubleTap() {
        scope.launch {
            val monitoringEnabled = settingsRepository.settings.first().monitoringEnabled
            if (!monitoringEnabled) return@launch
            when (_state.value.mode) {
                AppMode.Monitoring -> enterIdle("headset_double")
                else -> enterMonitoring("headset_double")
            }
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
}

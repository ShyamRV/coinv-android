package com.coinv.app.ui.profile

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinv.app.data.local.CoinVDatabase
import com.coinv.app.data.local.entity.LearningItemEntity
import com.coinv.app.data.local.entity.UserProfileEntity
import com.coinv.app.core.intervention.BehaviorStat
import com.coinv.app.core.intervention.InterventionCoordinator
import com.coinv.app.data.repository.VaultMemoryRepository
import com.coinv.app.data.repository.SemanticMemoryRepository
import com.coinv.app.data.repository.ProfileRepository
import com.coinv.app.data.settings.AppSettings
import com.coinv.app.data.settings.SettingsRepository
import com.coinv.app.voice.VoiceListeningService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

val AI_COACHES = listOf(
    "Founder Coach",
    "Productivity Coach",
    "Learning Coach",
    "Career Coach",
    "Thinking Coach",
    "Decision Coach"
)

data class ProfileUiState(
    val profile: UserProfileEntity? = null,
    val learning: List<LearningItemEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val semanticMemoryCount: Int = 0
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val vaultMemoryRepository: VaultMemoryRepository,
    private val semanticMemoryRepository: SemanticMemoryRepository,
    private val interventionCoordinator: InterventionCoordinator,
    private val settingsRepository: SettingsRepository,
    private val database: CoinVDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.observeProfile(),
        repository.observeLearning(),
        settingsRepository.settings,
        semanticMemoryRepository.observeCount()
    ) { profile, learning, settings, memoryCount ->
        ProfileUiState(profile, learning, settings, memoryCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    private val _behaviorStats = MutableStateFlow<List<BehaviorStat>>(emptyList())
    val behaviorStats = _behaviorStats.asStateFlow()

    val valueMemories = semanticMemoryRepository.observeValueMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshBehaviorStats()
    }

    fun refreshBehaviorStats() {
        viewModelScope.launch {
            _behaviorStats.value = interventionCoordinator.loadBehaviorStats()
        }
    }

    fun selectCoach(coach: String) {
        viewModelScope.launch { repository.updateCoach(coach) }
    }

    fun updateName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.updateName(name.trim()) }
    }

    fun updateListeningMode(mode: String) {
        viewModelScope.launch {
            val wakeWord = mode == "wake_word"
            val continuous = mode == "always_listening"
            repository.updateListening(mode, wakeWord, continuous)
            settingsRepository.setVoiceMode(mode)
            settingsRepository.setWakeWord(wakeWord)
            settingsRepository.setContinuousListening(continuous)
            syncListeningService(mode)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMonitoringEnabled(enabled) }
    }

    fun setLocalOnlyProcessing(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLocalOnlyProcessing(enabled) }
    }

    fun setPrivacyAnalytics(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPrivacyAnalytics(enabled) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifications(enabled) }
    }

    fun setMemoryRetention(days: Int) {
        viewModelScope.launch { settingsRepository.setMemoryRetention(days) }
    }

    fun clearMemory() {
        viewModelScope.launch {
            vaultMemoryRepository.clearAll()
            semanticMemoryRepository.clearAll()
        }
    }

    fun resetApp() {
        viewModelScope.launch {
            VoiceListeningService.stop(context)
            database.clearAllTables()
            settingsRepository.clearAll()
            database.profileDao().insert(
                UserProfileEntity(
                    name = "Shyam",
                    cognitiveState = "Ready",
                    activeCoach = "Founder Coach",
                    listeningMode = "push_to_talk"
                )
            )
        }
    }

    fun exportData() {
        viewModelScope.launch {
            val profile = repository.observeProfile().first()
            val json = buildString {
                append("{\n")
                append("  \"name\": \"${profile?.name ?: "Shyam"}\",\n")
                append("  \"cognitiveState\": \"${profile?.cognitiveState ?: "Ready"}\",\n")
                append("  \"activeCoach\": \"${profile?.activeCoach ?: ""}\",\n")
                append("  \"listeningMode\": \"${profile?.listeningMode ?: "push_to_talk"}\"\n")
                append("}")
            }
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_SUBJECT, "CoinV Profile Export")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(share, "Export CoinV data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun syncListeningService(mode: String) {
        when (mode) {
            "always_listening", "wake_word" -> VoiceListeningService.start(context, mode)
            else -> VoiceListeningService.stop(context)
        }
    }
}

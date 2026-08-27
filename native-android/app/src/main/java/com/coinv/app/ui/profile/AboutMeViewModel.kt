package com.coinv.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinv.app.data.local.entity.MemoryEntity
import com.coinv.app.data.repository.ProfileRepository
import com.coinv.app.data.repository.SemanticMemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutMeUiState(
    val fields: Map<AboutMeField, String> = AboutMeField.entries.associateWith { "" },
    val saveStatus: Map<AboutMeField, String?> = AboutMeField.entries.associateWith { null }
)

@HiltViewModel
class AboutMeViewModel @Inject constructor(
    private val semanticMemoryRepository: SemanticMemoryRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutMeUiState())
    val uiState: StateFlow<AboutMeUiState> = _uiState.asStateFlow()

    val valueMemories: StateFlow<List<MemoryEntity>> =
        semanticMemoryRepository.observeValueMemories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { loadFields() }
    }

    fun updateField(field: AboutMeField, value: String) {
        _uiState.value = _uiState.value.copy(
            fields = _uiState.value.fields + (field to value),
            saveStatus = _uiState.value.saveStatus + (field to null)
        )
    }

    fun saveField(field: AboutMeField) {
        val raw = _uiState.value.fields[field]?.trim().orEmpty()
        if (raw.isBlank()) return

        viewModelScope.launch {
            val saved = semanticMemoryRepository.rememberProfileField(
                sourceId = field.sourceId,
                content = field.toMemoryContent(raw),
                importance = 1.0f
            )
            if (saved && field == AboutMeField.NAME) {
                profileRepository.updateName(raw)
            }
            _uiState.value = _uiState.value.copy(
                saveStatus = _uiState.value.saveStatus + (
                    field to if (saved) "Saved" else "Save failed — check GEMINI_API_KEY"
                    )
            )
        }
    }

    private suspend fun loadFields() {
        val loaded = AboutMeField.entries.associateWith { field ->
            semanticMemoryRepository.getProfileField(field.sourceId)
                ?.let { field.displayFromMemory(it.content) }
                .orEmpty()
        }.toMutableMap()
        if (loaded[AboutMeField.NAME].isNullOrBlank()) {
            val profileName = profileRepository.observeProfile().first()?.name
            if (!profileName.isNullOrBlank()) {
                loaded[AboutMeField.NAME] = profileName
            }
        }
        _uiState.value = _uiState.value.copy(fields = loaded)
    }
}

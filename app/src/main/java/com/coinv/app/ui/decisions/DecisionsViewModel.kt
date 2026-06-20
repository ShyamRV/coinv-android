package com.coinv.app.ui.decisions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinv.app.data.local.entity.DecisionEntity
import com.coinv.app.data.local.entity.GoalEntity
import com.coinv.app.data.local.entity.TaskEntity
import com.coinv.app.data.repository.DecisionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DecisionsUiState(
    val decisions: List<DecisionEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val isAnalyzing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DecisionsViewModel @Inject constructor(
    private val repository: DecisionRepository
) : ViewModel() {

    private val analyzing = MutableStateFlow(false)
    private val errorState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DecisionsUiState> = combine(
        repository.observeDecisions(),
        repository.observeGoals(),
        analyzing,
        errorState
    ) { decisions, goals, loading, error ->
        DecisionsUiState(decisions, goals, loading, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DecisionsUiState())

    fun observeTasks(goalId: Long): StateFlow<List<TaskEntity>> =
        repository.observeTasks(goalId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createDecision(question: String, context: String = "") {
        if (question.isBlank()) return
        viewModelScope.launch {
            analyzing.value = true
            errorState.value = null
            try {
                repository.createDecision(question, context)
            } catch (e: Exception) {
                errorState.value = e.message ?: "Failed to analyze decision"
            } finally {
                analyzing.value = false
            }
        }
    }

    fun createGoal(title: String, description: String, tasks: List<String> = emptyList()) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.createGoal(title, description, tasks) }
    }

    fun addTask(goalId: Long, title: String) {
        viewModelScope.launch { repository.addTask(goalId, title) }
    }

    fun toggleTask(taskId: Long, goalId: Long, completed: Boolean) {
        viewModelScope.launch { repository.toggleTask(taskId, goalId, completed) }
    }

    fun recordOutcome(id: Long, outcome: String) {
        viewModelScope.launch { repository.recordOutcome(id, outcome) }
    }

    fun clearError() {
        errorState.value = null
    }
}

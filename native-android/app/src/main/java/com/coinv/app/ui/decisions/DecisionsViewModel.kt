package com.coinv.app.ui.decisions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinv.app.data.local.entity.DecisionEntity
import com.coinv.app.data.local.entity.DecisionStatuses
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
    val similarPast: List<DecisionEntity> = emptyList(),
    val isAnalyzing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DecisionsViewModel @Inject constructor(
    private val repository: DecisionRepository
) : ViewModel() {

    private val analyzing = MutableStateFlow(false)
    private val errorState = MutableStateFlow<String?>(null)
    private val similarPast = MutableStateFlow<List<DecisionEntity>>(emptyList())

    val uiState: StateFlow<DecisionsUiState> = combine(
        repository.observeDecisions(),
        repository.observeGoals(),
        analyzing,
        errorState,
        similarPast
    ) { decisions, goals, loading, error, similar ->
        DecisionsUiState(decisions, goals, similar, loading, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DecisionsUiState())

    fun observeTasks(goalId: Long): StateFlow<List<TaskEntity>> =
        repository.observeTasks(goalId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createDecision(question: String, context: String = "") {
        if (question.isBlank()) return
        viewModelScope.launch {
            analyzing.value = true
            errorState.value = null
            similarPast.value = emptyList()
            try {
                // Surface pattern matches before the fresh analysis lands in the list.
                similarPast.value = repository.findSimilarPastDecisions(question.trim())
                repository.createDecision(question.trim(), context.trim())
            } catch (e: Exception) {
                errorState.value = "Analysis couldn't be completed, try again"
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

    fun recordOutcome(id: Long, status: String, notes: String? = null) {
        viewModelScope.launch { repository.recordOutcome(id, status, notes) }
    }

    /** Test helper: mark resolved so pattern retrieval can match against it. */
    fun markResolvedForTesting(id: Long, status: String = DecisionStatuses.RESOLVED_MIXED) {
        recordOutcome(id, status, notes = "Marked for pattern testing")
    }

    fun clearError() {
        errorState.value = null
    }

    fun clearSimilarPast() {
        similarPast.value = emptyList()
    }
}

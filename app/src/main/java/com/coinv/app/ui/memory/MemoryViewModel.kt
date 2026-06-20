package com.coinv.app.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinv.app.data.local.entity.MemoryEntity
import com.coinv.app.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val repository: MemoryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val memories: StateFlow<List<MemoryEntity>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.observeAll() else repository.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun search(query: String) {
        searchQuery.value = query
    }

    fun saveMemory(title: String, content: String, category: String = "note", tags: String = "") {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.save(
                title = title.ifBlank { "Memory" },
                content = content,
                category = category,
                tags = tags
            )
        }
    }
}

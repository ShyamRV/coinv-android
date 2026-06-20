package com.coinv.app.ui.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinv.app.BuildConfig
import com.coinv.app.data.Message
import com.coinv.app.data.local.entity.ConversationEntity
import com.coinv.app.data.local.entity.MessageEntity
import com.coinv.app.data.local.entity.VoiceSessionEntity
import com.coinv.app.data.repository.ProfileRepository
import com.coinv.app.data.repository.VoiceRepository
import com.coinv.app.llm.askAsiOne
import com.coinv.app.llm.coachSystemPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> get() = _messages

    val recentSessions: StateFlow<List<VoiceSessionEntity>> = voiceRepository.observeRecentSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = voiceRepository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyMessages: StateFlow<List<MessageEntity>> = voiceRepository.observeRecentMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var status by mutableStateOf("idle")
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    var liveTranscript by mutableStateOf("")
        private set

    var listeningMode by mutableStateOf("push_to_talk")
        private set

    private var activeCoach = "Founder Coach"
    private var sessionStartMs = 0L

    init {
        viewModelScope.launch {
            val convId = voiceRepository.ensureConversation()
            _messages.clear()
            _messages.addAll(voiceRepository.getMessages(convId))
        }
        viewModelScope.launch {
            profileRepository.observeProfile().collect { profile ->
                profile?.let {
                    listeningMode = it.listeningMode
                    activeCoach = it.activeCoach
                }
            }
        }
    }

    fun clearError() { errorText = null }
    fun updateStatus(value: String) { status = value }

    fun updateListeningMode(mode: String) {
        listeningMode = mode
    }

    fun onListeningStarted() {
        sessionStartMs = System.currentTimeMillis()
        liveTranscript = ""
    }

    fun onUserSpeechResult(text: String) {
        liveTranscript = text
        _messages.add(Message("user", text))
        status = "thinking"
        errorText = null

        viewModelScope.launch {
            voiceRepository.saveMessage("user", text)
            val prompt = coachSystemPrompt(activeCoach)
            val result = withContext(Dispatchers.IO) {
                askAsiOne(BuildConfig.ASI_ONE_API_KEY, prompt, _messages.toList())
            }

            if (result.isSuccess) {
                val responseText = result.getOrThrow()
                _messages.add(Message("assistant", responseText))
                voiceRepository.saveMessage("assistant", responseText)
                status = "speaking"
            } else {
                status = "error"
                errorText = result.exceptionOrNull()?.message ?: "Couldn't reach the AI"
            }
        }
    }

    fun onSpeechError(message: String) {
        status = "error"
        errorText = message
    }

    fun onSpeakingComplete() {
        viewModelScope.launch {
            val duration = System.currentTimeMillis() - sessionStartMs
            if (liveTranscript.isNotBlank()) {
                voiceRepository.saveVoiceSession(liveTranscript, duration, listeningMode)
            }
        }
        status = when (listeningMode) {
            "always_listening", "wake_word" -> "monitoring"
            else -> "idle"
        }
    }

    fun captureIdea() {
        val lastUser = _messages.lastOrNull { it.role == "user" }?.text ?: return
        viewModelScope.launch { voiceRepository.captureIdea(lastUser) }
    }

    fun captureIdeaText(text: String) {
        viewModelScope.launch { voiceRepository.captureIdea(text) }
    }

    fun onPartialTranscript(text: String) {
        liveTranscript = text
    }

    fun sendPrompt(prompt: String) {
        onUserSpeechResult(prompt)
    }

    fun startNewSession() {
        viewModelScope.launch {
            voiceRepository.startNewConversation("Session ${System.currentTimeMillis()}")
            _messages.clear()
        }
    }
}

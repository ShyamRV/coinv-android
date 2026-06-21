package com.coinv.app.ui.voice

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinv.app.BuildConfig
import com.coinv.app.core.intervention.InterventionCoordinator
import com.coinv.app.core.intervention.InterventionSpeech
import com.coinv.app.data.Message
import com.coinv.app.data.local.entity.ConversationEntity
import com.coinv.app.data.local.entity.MessageEntity
import com.coinv.app.data.local.entity.VoiceSessionEntity
import com.coinv.app.data.repository.ProfileRepository
import com.coinv.app.data.repository.SemanticMemoryRepository
import com.coinv.app.data.repository.VoiceRepository
import com.coinv.app.data.settings.SettingsRepository
import com.coinv.app.domain.AppMode
import com.coinv.app.domain.ModeController
import com.coinv.app.domain.ModeState
import com.coinv.app.domain.VoicePhase
import com.coinv.app.engine.ContextEngine
import com.coinv.app.engine.PersonalizationEngine
import com.coinv.app.llm.ContextAssembler
import com.coinv.app.llm.askAsiOne
import com.coinv.app.llm.coachSystemPrompt
import com.coinv.app.voice.VoiceListeningService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

private val REMEMBER_THAT_PREFIX = Regex("^remember that\\s+", RegexOption.IGNORE_CASE)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceRepository: VoiceRepository,
    profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val modeController: ModeController,
    private val contextEngine: ContextEngine,
    private val personalizationEngine: PersonalizationEngine,
    private val semanticMemoryRepository: SemanticMemoryRepository,
    private val contextAssembler: ContextAssembler,
    private val interventionCoordinator: InterventionCoordinator
) : ViewModel() {

    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> get() = _messages

    val modeState: StateFlow<ModeState> = modeController.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModeState())

    val recentSessions: StateFlow<List<VoiceSessionEntity>> = voiceRepository.observeRecentSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = voiceRepository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyMessages: StateFlow<List<MessageEntity>> = voiceRepository.observeRecentMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var liveTranscript by mutableStateOf("")
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    private var activeCoach = "Founder Coach"
    private var sessionStartMs = 0L
    private var queuedMainLlmUserText: String? = null
    private var userMessageAlreadyAdded = false

    private val _micRestart = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val micRestart: SharedFlow<Unit> = _micRestart.asSharedFlow()

    init {
        interventionCoordinator.bindScope(viewModelScope)
        viewModelScope.launch {
            val convId = voiceRepository.ensureConversation()
            _messages.clear()
            _messages.addAll(voiceRepository.getMessages(convId))
            personalizationEngine.ensureProfile()
        }
        viewModelScope.launch {
            profileRepository.observeProfile().collect { profile ->
                profile?.let { activeCoach = it.activeCoach }
            }
        }
    }

    fun onAppResume() {
        viewModelScope.launch {
            val speech = interventionCoordinator.checkPromiseFollowUpsOnResume() ?: return@launch
            deliverInterventionSpeech(speech, userText = "", runMainLlmAfter = false, addUserFirst = false)
        }
    }

    fun clearError() {
        errorText = null
        modeController.clearError()
    }

    fun onListeningStarted() {
        sessionStartMs = System.currentTimeMillis()
        liveTranscript = ""
        modeController.setPhase(VoicePhase.Capturing)
    }

    fun onPartialTranscript(text: String) {
        liveTranscript = text
        val mode = modeController.state.value.mode
        if (mode == AppMode.Monitoring && text.isNotBlank()) {
            viewModelScope.launch {
                contextEngine.recordSpeech(text, AppMode.Monitoring, source = "partial")
            }
        }
    }

    fun onUserSpeechResult(text: String) {
        liveTranscript = text
        val mode = modeController.state.value.mode

        viewModelScope.launch {
            contextEngine.recordSpeech(text, mode)
            interventionCoordinator.resolvePriorOutcome(text)

            if (handleRememberThatCommand(text)) return@launch

            val isExplicitInMonitoring =
                mode == AppMode.Monitoring && contextEngine.isExplicitUserRequest(text)
            val isConversational = mode == AppMode.Listening || isExplicitInMonitoring

            if (mode == AppMode.Monitoring && !isExplicitInMonitoring) {
                withContext(Dispatchers.IO) {
                    semanticMemoryRepository.remember(
                        content = text,
                        layer = "episodic",
                        sourceType = "monitoring",
                        importance = 0.3f
                    )
                    interventionCoordinator.runPreConversationPassive(text, isConversationalMode = false)
                }
                personalizationEngine.recordInteractionOutcome(
                    topic = "monitoring",
                    hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    wasSuccessful = true
                )
                modeController.setPhase(VoicePhase.Capturing)
                _micRestart.tryEmit(Unit)
                return@launch
            }

            if (interventionCoordinator.isDevilsAdvocateCommand(text)) {
                handleDevilsAdvocate(text)
                return@launch
            }

            val preSpeech = interventionCoordinator.runPreConversationPassive(text, isConversationalMode = isConversational)
            if (preSpeech != null) {
                deliverInterventionSpeech(
                    preSpeech,
                    userText = text,
                    runMainLlmAfter = preSpeech.continueToMainLlm,
                    addUserFirst = false
                )
                if (!preSpeech.continueToMainLlm) return@launch
            }

            addUserMessage(text)

            val biasSpeech = interventionCoordinator.tryBiasSpotter(_messages.toList())
            if (biasSpeech != null) {
                deliverInterventionSpeech(
                    biasSpeech,
                    userText = text,
                    runMainLlmAfter = true,
                    addUserFirst = true
                )
                return@launch
            }

            runMainLlm(text)
        }
    }

    private suspend fun handleDevilsAdvocate(text: String) {
        addUserMessage(text)
        val speech = interventionCoordinator.runDevilsAdvocate(text, _messages.toList(), activeCoach)
            ?: run {
                runMainLlm(text)
                return
            }
        _messages.add(Message("assistant", speech.text))
        voiceRepository.saveMessage("assistant", speech.text)
        modeController.setPhase(VoicePhase.Speaking)
    }

    private suspend fun deliverInterventionSpeech(
        speech: InterventionSpeech,
        userText: String,
        runMainLlmAfter: Boolean,
        addUserFirst: Boolean
    ) {
        _messages.add(Message("assistant", speech.text))
        voiceRepository.saveMessage("assistant", speech.text)
        if (runMainLlmAfter) {
            queuedMainLlmUserText = userText
            userMessageAlreadyAdded = addUserFirst
        }
        modeController.setPhase(VoicePhase.Speaking)
    }

    private suspend fun addUserMessage(text: String) {
        if (_messages.lastOrNull()?.role == "user" && _messages.last().text == text) return
        _messages.add(Message("user", text))
        voiceRepository.saveMessage("user", text)
    }

    private suspend fun runMainLlm(text: String) {
        modeController.setPhase(VoicePhase.Thinking)
        errorText = null

        val profile = personalizationEngine.ensureProfile()
        val basePrompt = coachSystemPrompt(activeCoach)
        val contextBlock = contextAssembler.assembleContext(text)
        val lengthHint = personalizationEngine.responseLengthHint(profile)
        val enrichedSystemPrompt = buildString {
            append(basePrompt)
            if (contextBlock.isNotEmpty()) {
                append("\n\n")
                append(contextBlock)
            }
            append(" ")
            append(lengthHint)
        }
        val result = withContext(Dispatchers.IO) {
            askAsiOne(BuildConfig.ASI_ONE_API_KEY, enrichedSystemPrompt, _messages.toList())
        }

        if (result.isSuccess) {
            val responseText = result.getOrThrow()
            _messages.add(Message("assistant", responseText))
            voiceRepository.saveMessage("assistant", responseText)
            modeController.setPhase(VoicePhase.Speaking)
            personalizationEngine.recordInteractionOutcome(
                topic = inferTopic(text),
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                wasSuccessful = true
            )
            persistConversationMemories(text, responseText)
        } else {
            modeController.setPhase(VoicePhase.Error, result.exceptionOrNull()?.message)
            errorText = result.exceptionOrNull()?.message ?: "Couldn't reach the AI"
        }
    }

    private fun handleRememberThatCommand(text: String): Boolean {
        val match = REMEMBER_THAT_PREFIX.find(text.trim()) ?: return false
        val valueContent = text.trim().substring(match.range.last + 1).trim()
        if (valueContent.isBlank()) return false

        _messages.add(Message("user", text))
        viewModelScope.launch {
            voiceRepository.saveMessage("user", text)
            semanticMemoryRepository.remember(
                content = valueContent,
                layer = "value",
                sourceType = "user_stated",
                importance = 1.0f
            )
            val confirmation = "Got it, I'll remember that."
            _messages.add(Message("assistant", confirmation))
            voiceRepository.saveMessage("assistant", confirmation)
            modeController.setPhase(VoicePhase.Speaking)
        }
        return true
    }

    private fun persistConversationMemories(userText: String, assistantText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            semanticMemoryRepository.remember(
                content = userText,
                layer = "episodic",
                sourceType = "conversation"
            )
            semanticMemoryRepository.remember(
                content = assistantText,
                layer = "episodic",
                sourceType = "conversation"
            )
        }
    }

    fun onSpeechError(message: String) {
        if (message.contains("No speech") && modeController.state.value.mode == AppMode.Monitoring) {
            modeController.setPhase(VoicePhase.Capturing)
            return
        }
        modeController.setPhase(VoicePhase.Error, message)
        errorText = message
    }

    fun onSpeakingComplete() {
        val queued = queuedMainLlmUserText
        if (queued != null) {
            queuedMainLlmUserText = null
            viewModelScope.launch {
                if (!userMessageAlreadyAdded) addUserMessage(queued)
                userMessageAlreadyAdded = false
                runMainLlm(queued)
            }
            return
        }

        viewModelScope.launch {
            val duration = System.currentTimeMillis() - sessionStartMs
            if (liveTranscript.isNotBlank()) {
                val modeLabel = modeController.state.value.mode.name.lowercase()
                voiceRepository.saveVoiceSession(liveTranscript, duration, modeLabel)
            }
        }
        val mode = modeController.state.value.mode
        when (mode) {
            AppMode.Listening -> modeController.setPhase(VoicePhase.Capturing)
            AppMode.Monitoring -> modeController.setPhase(VoicePhase.Capturing)
            AppMode.Idle -> modeController.enterIdle("speaking_complete")
        }
    }

    fun enterListening(source: String) {
        modeController.enterListening(source)
        VoiceListeningService.start(context, AppMode.Listening.name.lowercase())
    }

    fun enterMonitoring(source: String) {
        viewModelScope.launch {
            if (!settingsRepository.settings.first().monitoringEnabled) return@launch
            modeController.enterMonitoring(source)
            VoiceListeningService.start(context, AppMode.Monitoring.name.lowercase())
        }
    }

    fun enterIdle(source: String) {
        modeController.enterIdle(source)
        VoiceListeningService.stop(context)
    }

    fun toggleFromOrb() {
        when (modeController.state.value.mode) {
            AppMode.Idle -> enterListening("orb")
            AppMode.Listening -> enterIdle("orb")
            AppMode.Monitoring -> enterIdle("orb")
        }
    }

    fun recordFeedback(accepted: Boolean, topic: String = "") {
        viewModelScope.launch {
            personalizationEngine.recordFeedback(
                targetType = "response",
                targetId = System.currentTimeMillis().toString(),
                accepted = accepted,
                topic = topic
            )
        }
    }

    fun captureIdea() {
        val lastUser = _messages.lastOrNull { it.role == "user" }?.text ?: return
        viewModelScope.launch { voiceRepository.captureIdea(lastUser) }
    }

    fun captureIdeaText(text: String) {
        viewModelScope.launch { voiceRepository.captureIdea(text) }
    }

    fun sendPrompt(prompt: String) {
        enterListening("prompt")
        onUserSpeechResult(prompt)
    }

    fun startNewSession() {
        viewModelScope.launch {
            voiceRepository.startNewConversation("Session ${System.currentTimeMillis()}")
            _messages.clear()
        }
    }

    private fun inferTopic(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("decision") -> "decisions"
            lower.contains("goal") -> "goals"
            lower.contains("learn") -> "learning"
            lower.contains("business") || lower.contains("startup") -> "business"
            else -> "general"
        }
    }
}

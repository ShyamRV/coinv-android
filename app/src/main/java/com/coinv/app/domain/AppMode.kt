package com.coinv.app.domain

enum class AppMode {
    Idle,
    Listening,
    Monitoring;

    fun displayLabel(): String = when (this) {
        Idle -> "Idle"
        Listening -> "Listening"
        Monitoring -> "Monitoring"
    }
}

enum class VoicePhase {
    None,
    Capturing,
    Thinking,
    Speaking,
    Error
}

data class ModeState(
    val mode: AppMode = AppMode.Idle,
    val phase: VoicePhase = VoicePhase.None,
    val errorMessage: String? = null,
    val activatedBy: String = "system"
) {
    fun orbStatus(): String = when {
        phase == VoicePhase.Error -> "error"
        mode == AppMode.Monitoring && phase == VoicePhase.Capturing -> "monitoring"
        mode == AppMode.Monitoring -> "monitoring"
        phase == VoicePhase.Thinking -> "thinking"
        phase == VoicePhase.Speaking -> "speaking"
        phase == VoicePhase.Capturing -> "listening"
        mode == AppMode.Listening -> "listening"
        else -> "idle"
    }

    fun isMicActive(): Boolean =
        phase == VoicePhase.Capturing ||
            (mode == AppMode.Monitoring && phase == VoicePhase.None)

    fun shouldRespondToSpeech(): Boolean = mode == AppMode.Listening

    fun shouldCollectContextOnly(): Boolean =
        mode == AppMode.Monitoring && phase != VoicePhase.Thinking && phase != VoicePhase.Speaking
}

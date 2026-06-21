package com.coinv.app.ui.profile

enum class AboutMeField(
    val sourceId: Long,
    val label: String,
    val hint: String
) {
    NAME(
        sourceId = -1L,
        label = "What's your name?",
        hint = "How should CoinV address you?"
    ),
    CURRENT_FOCUS(
        sourceId = -2L,
        label = "What do you do / are you working on right now?",
        hint = "Current role, project, or focus"
    ),
    TOP_PRIORITY(
        sourceId = -3L,
        label = "What matters most to you right now?",
        hint = "Values, priorities, what you're optimizing for"
    ),
    GENERAL_NOTE(
        sourceId = -4L,
        label = "Anything CoinV should always keep in mind about you?",
        hint = "Preferences, constraints, context that should persist"
    );

    fun toMemoryContent(rawInput: String): String {
        val value = rawInput.trim()
        return when (this) {
            NAME -> "User's name is $value"
            CURRENT_FOCUS -> "User is currently working on: $value"
            TOP_PRIORITY -> {
                val normalized = value
                    .removePrefix("my ")
                    .removePrefix("My ")
                    .trim()
                "User has stated that $normalized matter most to them right now."
            }
            GENERAL_NOTE -> "User wants CoinV to always keep in mind: $value"
        }
    }

    fun displayFromMemory(content: String): String = when (this) {
        NAME -> content.removePrefix("User's name is ").trim()
        CURRENT_FOCUS -> content.removePrefix("User is currently working on: ").trim()
        TOP_PRIORITY -> {
            val prefix = "User has stated that "
            val suffix = " matter most to them right now."
            content.removePrefix(prefix).removeSuffix(suffix).trim()
        }
        GENERAL_NOTE -> content.removePrefix("User wants CoinV to always keep in mind: ").trim()
    }
}

package com.coinv.app.core.intervention

object InterventionTypes {
    const val DEVILS_ADVOCATE = "devils_advocate"
    const val BIAS_SPOTTER = "bias_spotter"
    const val PROMISE_TRACKER = "promise_tracker"
    const val COMMITMENT_GUARD = "commitment_guard"
    const val DECISION_FOLLOWUP = "decision_followup"

    val ALL = listOf(
        DEVILS_ADVOCATE,
        BIAS_SPOTTER,
        PROMISE_TRACKER,
        COMMITMENT_GUARD,
        DECISION_FOLLOWUP
    )
}

object InterventionOutcomes {
    const val SHOWN = "shown"
    const val DISMISSED = "dismissed"
    const val ACTED_ON = "acted_on"
    const val PENDING = "pending"
}

object InterventionConstants {
    const val PROMISE_FOLLOWUP_DAYS = 14
    const val COMMITMENT_WARNING_THRESHOLD = 4
    const val COMMITMENT_WINDOW_DAYS = 7
    const val OUTCOME_RESOLVE_MS = 120_000L
    const val PROMISE_EXPIRE_EXTRA_DAYS = 30
    const val STATS_WINDOW_DAYS = 30
}

package com.coinv.app.core.intervention

import com.coinv.app.data.local.dao.InterventionDao

/**
 * Falsification mechanism: if a feature is consistently dismissed, stop firing for this user.
 * No fallback message — the feature goes silent.
 */
suspend fun shouldFire(type: String, dao: InterventionDao): Boolean {
    val recent = dao.lastFiveByType(type)
    if (recent.size < 3) return true
    val dismissedCount = recent.count { it.outcome == InterventionOutcomes.DISMISSED }
    if (dismissedCount >= 3) return false
    return true
}

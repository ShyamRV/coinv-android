package com.coinv.app.core.intervention

import com.coinv.app.data.local.dao.InterventionDao
import com.coinv.app.data.local.entity.InterventionEntity

suspend fun logShown(
    type: String,
    triggerContext: String,
    content: String,
    dao: InterventionDao
): Long {
    val now = System.currentTimeMillis()
    return dao.insert(
        InterventionEntity(
            type = type,
            triggerContext = triggerContext.take(500),
            content = content.take(1000),
            timestamp = now,
            outcome = InterventionOutcomes.SHOWN,
            outcomeTimestamp = null
        )
    )
}

suspend fun resolveOutcome(id: Long, outcome: String, dao: InterventionDao) {
    val existing = dao.getById(id) ?: return
    if (existing.outcome != InterventionOutcomes.SHOWN) return
    dao.update(
        existing.copy(
            outcome = outcome,
            outcomeTimestamp = System.currentTimeMillis()
        )
    )
}

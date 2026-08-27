package com.coinv.app.core.intervention

import com.coinv.app.BuildConfig
import com.coinv.app.data.Message
import com.coinv.app.data.local.dao.DecisionDao
import com.coinv.app.data.local.dao.InterventionDao
import com.coinv.app.data.local.dao.PromiseDao
import com.coinv.app.data.local.entity.DecisionEntity
import com.coinv.app.data.local.entity.DecisionStatuses
import com.coinv.app.data.local.entity.PromiseEntity
import com.coinv.app.llm.ContextAssembler
import com.coinv.app.llm.askAsiOne
import com.coinv.app.llm.coachSystemPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class InterventionSpeech(
    val text: String,
    val interventionId: Long,
    val type: String,
    val continueToMainLlm: Boolean = false
)

data class BehaviorStat(
    val type: String,
    val label: String,
    val shownCount: Int,
    val dismissedCount: Int,
    val isActive: Boolean
)

@Singleton
class InterventionCoordinator @Inject constructor(
    private val interventionDao: InterventionDao,
    private val promiseDao: PromiseDao,
    private val decisionDao: DecisionDao,
    private val contextAssembler: ContextAssembler
) {
    private var pendingOutcomeId: Long? = null
    private var pendingOutcomeType: String? = null
    private var pendingOutcomeShownAt: Long = 0L
    private var dismissTimeoutJob: Job? = null
    private var suppressPromiseCaptureThisTurn = false
    private var pendingPromiseFollowUp: PromiseEntity? = null
    private var pendingPromiseInterventionId: Long? = null
    private var pendingDecisionFollowUp: DecisionEntity? = null
    private var pendingDecisionInterventionId: Long? = null

    fun bindScope(scope: CoroutineScope) {
        coordinatorScope = scope
    }

    private var coordinatorScope: CoroutineScope? = null

    /**
     * Shared app-open follow-up check: promises first, then decision outcomes.
     */
    suspend fun checkFollowUpsOnResume(): InterventionSpeech? {
        checkPromiseFollowUpsOnResume()?.let { return it }
        return checkDecisionFollowUpsOnResume()
    }

    suspend fun resolvePriorOutcome(userText: String) {
        pendingDecisionFollowUp?.let { decision ->
            handleDecisionFollowUpAnswer(userText, decision)
            return
        }

        pendingPromiseFollowUp?.let { promise ->
            handlePromiseFollowUpAnswer(userText, promise)
            return
        }

        val id = pendingOutcomeId ?: return
        val type = pendingOutcomeType ?: return
        val elapsed = System.currentTimeMillis() - pendingOutcomeShownAt
        if (elapsed > InterventionConstants.OUTCOME_RESOLVE_MS) return

        val outcome = when {
            isDismissPhrase(userText) -> InterventionOutcomes.DISMISSED
            type == InterventionTypes.PROMISE_TRACKER && isStopAskingPhrase(userText) ->
                InterventionOutcomes.DISMISSED
            type == InterventionTypes.DECISION_FOLLOWUP && isStopAskingPhrase(userText) ->
                InterventionOutcomes.DISMISSED
            type == InterventionTypes.PROMISE_TRACKER -> InterventionOutcomes.ACTED_ON
            type == InterventionTypes.DECISION_FOLLOWUP -> InterventionOutcomes.ACTED_ON
            else -> InterventionOutcomes.ACTED_ON
        }
        resolveOutcome(id, outcome, interventionDao)
        clearPendingOutcome()
    }

    fun isDevilsAdvocateCommand(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("play devil's advocate") ||
            lower.contains("play devils advocate") ||
            lower.contains("challenge me on this")
    }

    suspend fun runDevilsAdvocate(
        text: String,
        messages: List<Message>,
        activeCoach: String
    ): InterventionSpeech? {
        if (!shouldFire(InterventionTypes.DEVILS_ADVOCATE, interventionDao)) return null

        val position = messages.filter { it.role == "user" }.takeLast(2)
            .ifEmpty { listOf(Message("user", text)) }
        val basePrompt = coachSystemPrompt(activeCoach)
        val contextBlock = contextAssembler.assembleContext(text)
        val devilPrompt = buildString {
            append(basePrompt)
            if (contextBlock.isNotEmpty()) append("\n\n").append(contextBlock)
            append(
                "\n\nFor this response only, argue the strongest case AGAINST what the user just said. " +
                    "Be direct and substantive, not contrarian for its own sake. " +
                    "Keep it under 4 sentences since this will be spoken aloud."
            )
        }
        val result = askAsiOne(BuildConfig.ASI_ONE_API_KEY, devilPrompt, position)
        val response = result.getOrNull() ?: return null
        val id = logShown(
            InterventionTypes.DEVILS_ADVOCATE,
            position.joinToString(" ") { it.text },
            response,
            interventionDao
        )
        scheduleDismissTimeout(id, InterventionTypes.DEVILS_ADVOCATE)
        return InterventionSpeech(response, id, InterventionTypes.DEVILS_ADVOCATE)
    }

    /**
     * Passive pre-LLM checks for active conversation mode (Listening or explicit monitoring request).
     */
    suspend fun runPreConversationPassive(
        text: String,
        isConversationalMode: Boolean
    ): InterventionSpeech? {
        suppressPromiseCaptureThisTurn = false
        if (!isConversationalMode) {
            capturePromiseSilent(text)
            return null
        }

        val commitment = tryCommitmentGuard(text)
        if (commitment != null) {
            suppressPromiseCaptureThisTurn = true
            return commitment
        }

        capturePromiseSilent(text)
        return null
    }

    /**
     * Bias spotter: silent LLM check; speaks only if a clear bias is found.
     * Coarse keyword trigger — not true intent classification.
     */
    suspend fun tryBiasSpotter(messages: List<Message>): InterventionSpeech? {
        if (!shouldFire(InterventionTypes.BIAS_SPOTTER, interventionDao)) return null
        if (!hasDecisionLanguage(messages)) return null

        val excerpt = messages.takeLast(6).joinToString("\n") { "${it.role}: ${it.text}" }
        val query = messages.lastOrNull { it.role == "user" }?.text ?: excerpt
        val contextBlock = contextAssembler.assembleContext(query)
        val systemPrompt = buildString {
            append(
                "Review this statement for ONE of these specific patterns only: sunk cost fallacy, " +
                    "confirmation bias, recency bias, or overconfidence from a small sample. " +
                    "If you genuinely detect one clearly, respond with ONLY the pattern name and a " +
                    "one-sentence explanation tied to their specific words. " +
                    "If none clearly apply, respond with exactly: NONE."
            )
            if (contextBlock.isNotEmpty()) {
                append("\n\n")
                append(contextBlock)
            }
        }
        val result = askAsiOne(
            BuildConfig.ASI_ONE_API_KEY,
            systemPrompt,
            excerpt,
            maxTokens = 120
        )
        val response = result.getOrNull()?.trim() ?: return null
        if (response.equals("NONE", ignoreCase = true) || response.isBlank()) return null

        val id = logShown(InterventionTypes.BIAS_SPOTTER, excerpt.take(300), response, interventionDao)
        scheduleDismissTimeout(id, InterventionTypes.BIAS_SPOTTER)
        return InterventionSpeech(response, id, InterventionTypes.BIAS_SPOTTER, continueToMainLlm = true)
    }

    /** Silent promise capture — allowed in monitoring mode transcript stream. */
    suspend fun capturePromiseSilent(text: String) {
        if (suppressPromiseCaptureThisTurn) return
        if (!shouldFire(InterventionTypes.PROMISE_TRACKER, interventionDao)) return
        if (!hasPromiseLanguage(text)) return

        val sentence = extractSentenceWithTrigger(text)
        val now = System.currentTimeMillis()
        val followUpMs = now + TimeUnit.DAYS.toMillis(InterventionConstants.PROMISE_FOLLOWUP_DAYS.toLong())
        val interventionId = logPending(
            InterventionTypes.PROMISE_TRACKER,
            sentence,
            sentence,
            interventionDao
        )
        promiseDao.insert(
            PromiseEntity(
                text = sentence,
                capturedAt = now,
                followUpAt = followUpMs,
                status = "pending",
                interventionId = interventionId
            )
        )
    }

    suspend fun checkPromiseFollowUpsOnResume(): InterventionSpeech? {
        val now = System.currentTimeMillis()
        val expireBefore = now - TimeUnit.DAYS.toMillis(
            (InterventionConstants.PROMISE_FOLLOWUP_DAYS + InterventionConstants.PROMISE_EXPIRE_EXTRA_DAYS).toLong()
        )
        promiseDao.expireStale(expireBefore)

        val due = promiseDao.oldestDueFollowUp(now) ?: return null
        if (!shouldFire(InterventionTypes.PROMISE_TRACKER, interventionDao)) return null

        val weeksAgo = when {
            InterventionConstants.PROMISE_FOLLOWUP_DAYS >= 14 -> "Two weeks ago"
            else -> "Recently"
        }
        val prompt = "$weeksAgo you mentioned: '${due.text}'. Did that happen?"
        // Reuse capture-time ledger row; do not insert a second intervention at follow-up.
        val id = due.interventionId?.takeIf { markShown(it, prompt, interventionDao) }
            ?: logShown(InterventionTypes.PROMISE_TRACKER, due.text, prompt, interventionDao)
        pendingPromiseFollowUp = due.copy(status = "asked", interventionId = id)
        promiseDao.update(pendingPromiseFollowUp!!)
        pendingPromiseInterventionId = id
        scheduleDismissTimeout(id, InterventionTypes.PROMISE_TRACKER)
        return InterventionSpeech(prompt, id, InterventionTypes.PROMISE_TRACKER)
    }

    suspend fun checkDecisionFollowUpsOnResume(): InterventionSpeech? {
        if (!shouldFire(InterventionTypes.DECISION_FOLLOWUP, interventionDao)) return null
        val now = System.currentTimeMillis()
        val due = decisionDao.oldestDueFollowUp(now) ?: return null

        val prompt =
            "3 weeks ago you were deciding: '${due.question}'. How did that turn out — good, bad, or mixed?"
        val id = logShown(
            InterventionTypes.DECISION_FOLLOWUP,
            due.question,
            prompt,
            interventionDao
        )
        // Mark asked immediately so we never re-prompt indefinitely if they don't answer.
        decisionDao.markFollowUpAsked(due.id, now)
        pendingDecisionFollowUp = due
        pendingDecisionInterventionId = id
        scheduleDismissTimeout(id, InterventionTypes.DECISION_FOLLOWUP)
        return InterventionSpeech(prompt, id, InterventionTypes.DECISION_FOLLOWUP)
    }

    suspend fun loadBehaviorStats(): List<BehaviorStat> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(
            InterventionConstants.STATS_WINDOW_DAYS.toLong()
        )
        return InterventionTypes.ALL.map { type ->
            BehaviorStat(
                type = type,
                label = labelFor(type),
                shownCount = interventionDao.countShownSince(type, since),
                dismissedCount = interventionDao.countDismissedSince(type, since),
                isActive = shouldFire(type, interventionDao)
            )
        }
    }

    private suspend fun tryCommitmentGuard(text: String): InterventionSpeech? {
        if (!shouldFire(InterventionTypes.COMMITMENT_GUARD, interventionDao)) return null
        if (!hasCommitmentLanguage(text)) return null

        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(
            InterventionConstants.COMMITMENT_WINDOW_DAYS.toLong()
        )
        val count = promiseDao.countSince(weekAgo)
        if (count < InterventionConstants.COMMITMENT_WARNING_THRESHOLD) return null

        val warning =
            "That's the ${count + 1}th thing you've taken on this week — still want to add it?"
        val id = logShown(InterventionTypes.COMMITMENT_GUARD, text.take(300), warning, interventionDao)
        scheduleDismissTimeout(id, InterventionTypes.COMMITMENT_GUARD)
        suppressPromiseCaptureThisTurn = true
        return InterventionSpeech(warning, id, InterventionTypes.COMMITMENT_GUARD, continueToMainLlm = true)
    }

    private suspend fun handlePromiseFollowUpAnswer(userText: String, promise: PromiseEntity) {
        val lower = userText.lowercase()
        val interventionId = pendingPromiseInterventionId

        when {
            isStopAskingPhrase(userText) -> {
                interventionId?.let {
                    resolveOutcome(it, InterventionOutcomes.DISMISSED, interventionDao)
                }
            }
            isAffirmative(lower) -> {
                promiseDao.update(promise.copy(status = "confirmed_done"))
                interventionId?.let {
                    resolveOutcome(it, InterventionOutcomes.ACTED_ON, interventionDao)
                }
            }
            isNegative(lower) -> {
                promiseDao.update(promise.copy(status = "confirmed_not_done"))
                interventionId?.let {
                    resolveOutcome(it, InterventionOutcomes.ACTED_ON, interventionDao)
                }
            }
            else -> {
                promiseDao.update(promise.copy(status = "asked"))
                interventionId?.let {
                    resolveOutcome(it, InterventionOutcomes.ACTED_ON, interventionDao)
                }
            }
        }
        pendingPromiseFollowUp = null
        pendingPromiseInterventionId = null
        clearPendingOutcome()
    }

    private suspend fun handleDecisionFollowUpAnswer(userText: String, decision: DecisionEntity) {
        val interventionId = pendingDecisionInterventionId
        val lower = userText.lowercase()

        if (isStopAskingPhrase(userText) || isDismissPhrase(userText)) {
            interventionId?.let {
                resolveOutcome(it, InterventionOutcomes.DISMISSED, interventionDao)
            }
            pendingDecisionFollowUp = null
            pendingDecisionInterventionId = null
            clearPendingOutcome()
            return
        }

        val status = parseDecisionOutcomeStatus(lower)
        decisionDao.updateOutcome(
            id = decision.id,
            outcomeNotes = userText.trim().take(500),
            status = status,
            askedAt = decision.outcomeAskedAt ?: System.currentTimeMillis()
        )
        interventionId?.let {
            resolveOutcome(it, InterventionOutcomes.ACTED_ON, interventionDao)
        }
        pendingDecisionFollowUp = null
        pendingDecisionInterventionId = null
        clearPendingOutcome()
    }

    private fun parseDecisionOutcomeStatus(lower: String): String = when {
        lower.contains("abandon") || lower.contains("never did") || lower.contains("dropped") ->
            DecisionStatuses.ABANDONED
        lower.contains("good") || lower.contains("well") || lower.contains("great") ||
            lower.contains("worked") -> DecisionStatuses.RESOLVED_GOOD
        lower.contains("bad") || lower.contains("poorly") || lower.contains("regret") ||
            lower.contains("failed") -> DecisionStatuses.RESOLVED_BAD
        lower.contains("mixed") || lower.contains("okay") || lower.contains("ok") ->
            DecisionStatuses.RESOLVED_MIXED
        else -> DecisionStatuses.RESOLVED_MIXED
    }

    private fun scheduleDismissTimeout(id: Long, type: String) {
        pendingOutcomeId = id
        pendingOutcomeType = type
        pendingOutcomeShownAt = System.currentTimeMillis()
        dismissTimeoutJob?.cancel()
        dismissTimeoutJob = coordinatorScope?.launch {
            delay(InterventionConstants.OUTCOME_RESOLVE_MS)
            if (pendingOutcomeId == id) {
                resolveOutcome(id, InterventionOutcomes.DISMISSED, interventionDao)
                if (type == InterventionTypes.DECISION_FOLLOWUP) {
                    pendingDecisionFollowUp = null
                    pendingDecisionInterventionId = null
                }
                clearPendingOutcome()
            }
        }
    }

    private fun clearPendingOutcome() {
        pendingOutcomeId = null
        pendingOutcomeType = null
        dismissTimeoutJob?.cancel()
        dismissTimeoutJob = null
    }

    private fun hasDecisionLanguage(messages: List<Message>): Boolean {
        val recent = messages.takeLast(6).joinToString(" ") { it.text }.lowercase()
        return DECISION_KEYWORDS.any { recent.contains(it) }
    }

    private fun hasPromiseLanguage(text: String): Boolean {
        val lower = text.lowercase()
        return PROMISE_KEYWORDS.any { lower.contains(it) }
    }

    private fun hasCommitmentLanguage(text: String): Boolean {
        val lower = text.lowercase()
        return COMMITMENT_KEYWORDS.any { lower.contains(it) }
    }

    private fun extractSentenceWithTrigger(text: String): String {
        val sentences = text.split(Regex("[.!?]")).map { it.trim() }.filter { it.isNotBlank() }
        val lower = text.lowercase()
        val match = sentences.firstOrNull { sentence ->
            PROMISE_KEYWORDS.any { lower.contains(it) && sentence.lowercase().contains(it) }
        }
        return match ?: text.trim()
    }

    private fun isDismissPhrase(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.contains("never mind") || lower == "stop" || lower.contains("stop.")
    }

    private fun isStopAskingPhrase(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("stop asking me this") || lower.contains("stop asking me")
    }

    private fun isAffirmative(lower: String): Boolean =
        lower.contains("yes") || lower.contains("done") || lower.contains("did it") ||
            lower.contains("i did") || lower.contains("finished")

    private fun isNegative(lower: String): Boolean =
        lower.contains("no") || lower.contains("didn't") || lower.contains("did not") ||
            lower.contains("not yet") || lower.contains("haven't")

    private fun labelFor(type: String): String = when (type) {
        InterventionTypes.DEVILS_ADVOCATE -> "Devil's advocate"
        InterventionTypes.BIAS_SPOTTER -> "Bias spotter"
        InterventionTypes.PROMISE_TRACKER -> "Promise tracker"
        InterventionTypes.COMMITMENT_GUARD -> "Commitment guard"
        InterventionTypes.DECISION_FOLLOWUP -> "Decision follow-up"
        else -> type
    }

    companion object {
        private val DECISION_KEYWORDS = listOf(
            "should i", "i'm going to", "i've decided", "i think i'll"
        )
        private val PROMISE_KEYWORDS = listOf(
            "i'm going to start", "i'll finish by", "i need to", "i promise i'll"
        )
        private val COMMITMENT_KEYWORDS = listOf(
            "yes i'll do that", "sure i can", "okay i'll take that on"
        )
    }
}

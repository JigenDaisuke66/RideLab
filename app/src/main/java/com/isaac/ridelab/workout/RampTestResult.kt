package com.isaac.ridelab.workout

import com.isaac.ridelab.analytics.PowerAnalysisCalculator
import com.isaac.ridelab.analytics.TrainingAnalytics
import com.isaac.ridelab.data.model.RideSample
import com.isaac.ridelab.data.model.RideSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

enum class RampReviewPhase { READY, APPLYING, ACCEPTED, KEPT, FAILED }
data class RampTestResult(val sessionId: Long, val best60Watts: Double?, val estimatedFtp: Int?,
    val phase: RampReviewPhase = RampReviewPhase.READY) {
    val canAccept: Boolean get() = estimatedFtp != null && TrainingAnalytics.validFtp(estimatedFtp)
}

object RampTestCalculator {
    /** Stored measured watts only, during ramp effort. Never bridge a pause/skip.
     * Reuse existing exact 60s time-weighted best-effort and 1.5s continuity rules.
     */
    fun calculate(sessionId: Long, samples: List<RideSample>, ranges: List<EffortRange>): RampTestResult {
        val best = ranges.mapNotNull { range ->
            val rows = samples.filter { it.elapsedMillis in range.startMs..range.endMs }
            PowerAnalysisCalculator.calculate(rows, RideSession(startTimeEpochMillis = 0,
                durationMillis = range.endMs)).efforts.find { it.seconds == 60 }?.watts
        }.maxOrNull()
        return RampTestResult(sessionId, best, best?.let { (it * .75).roundToInt() })
    }
}

/** Process-local review survives service teardown/rebinding, not process death.
 * Only an explicit accept invokes the existing FTP settings save function.
 */
class RampResultReview(private val saveFtp: suspend (Int?) -> Unit) {
    private val mutable = MutableStateFlow<RampTestResult?>(null)
    val state = mutable.asStateFlow()
    fun publish(result: RampTestResult) { mutable.value = result }
    fun clear() { if (state.value?.phase != RampReviewPhase.APPLYING) mutable.value = null }
    fun keep() {
        val r = state.value ?: return
        if (r.phase in setOf(RampReviewPhase.READY, RampReviewPhase.FAILED)) mutable.value = r.copy(phase = RampReviewPhase.KEPT)
    }
    suspend fun accept() {
        val r = state.value ?: return
        if (!r.canAccept || r.phase !in setOf(RampReviewPhase.READY, RampReviewPhase.FAILED)) return
        mutable.value = r.copy(phase = RampReviewPhase.APPLYING)
        try {
            saveFtp(r.estimatedFtp)
            mutable.value = r.copy(phase = RampReviewPhase.ACCEPTED)
        } catch (e: CancellationException) {
            mutable.value = r; throw e
        } catch (e: Exception) {
            mutable.value = r.copy(phase = RampReviewPhase.FAILED)
            throw e
        }
    }
}

package com.isaac.ridelab.workout

import com.isaac.ridelab.ble.fec.TrainerErgProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WorkoutPhase { IDLE, PREPARING, ACTIVE, PAUSED, COMPLETED, CANCELLED, ERROR }
enum class WorkoutIssue { FTP_REQUIRED, TRAINER_UNAVAILABLE, CONTROL_FAILED, RIDE_FAILED, RESULT_FAILED }
data class WorkoutState(
    val phase: WorkoutPhase = WorkoutPhase.IDLE,
    val definition: WorkoutDefinition? = null,
    val ftpSnapshot: Int? = null,
    val stepIndex: Int = 0,
    val step: WorkoutStep? = null,
    val targetWatts: Int? = null,
    val stepElapsedMs: Long = 0,
    val elapsedMs: Long = 0,
    val nextStep: WorkoutStep? = null,
    val nextWatts: Int? = null,
    val issue: WorkoutIssue? = null,
) {
    val controlsTrainer: Boolean get() = phase in setOf(WorkoutPhase.PREPARING, WorkoutPhase.ACTIVE, WorkoutPhase.PAUSED)
    val remainingMs: Long get() = ((step?.durationSeconds ?: 0) * 1000L - stepElapsedMs).coerceAtLeast(0)
}
data class EffortRange(val startMs: Long, val endMs: Long)

/** Sole service-owned monotonic scheduler. All calls on its owner's main thread.
 * send returns whether one logical command was dispatched; asynchronous failure
 * is reported separately by controlFailed. No timers, reconnects or BLE here.
 */
class WorkoutController(private val profile: TrainerErgProfile, private val send: (Int) -> Boolean) {
    private val mutable = MutableStateFlow(WorkoutState())
    val state = mutable.asStateFlow()
    private var lastTick = 0L
    private var rideOrigin = 0L
    private var lastSent: Int? = null
    private var lastCommandAt = Long.MIN_VALUE
    private var rampStart: Long? = null
    private val ranges = mutableListOf<EffortRange>()
    fun effortRanges() = ranges.toList()
    fun reset() { if (!state.value.controlsTrainer) mutable.value = WorkoutState() }

    fun prepare(definition: WorkoutDefinition): Boolean {
        if (state.value.controlsTrainer) return false
        ranges.clear(); rampStart = null; lastSent = null
        mutable.value = WorkoutState(WorkoutPhase.PREPARING, definition)
        return true
    }
    fun fail(issue: WorkoutIssue) { mutable.value = state.value.copy(phase = WorkoutPhase.ERROR, issue = issue) }

    fun start(ftpSnapshot: Int?, now: Long, origin: Long): Boolean {
        val s = state.value
        if (s.phase != WorkoutPhase.PREPARING) return false
        val definition = s.definition ?: return false
        if (definition.requiresFtp && (ftpSnapshot == null || ftpSnapshot !in 50..500)) {
            fail(WorkoutIssue.FTP_REQUIRED); return false
        }
        lastTick = now; rideOrigin = origin
        mutable.value = s.copy(phase = WorkoutPhase.ACTIVE, ftpSnapshot = ftpSnapshot)
        enter(0, 0, now)
        return state.value.phase == WorkoutPhase.ACTIVE
    }
    private fun enter(index: Int, elapsed: Long, now: Long, force: Boolean = false) {
        val s = state.value
        val definition = s.definition ?: return
        val step = definition.step(index, s.ftpSnapshot, profile)
        if (step == null) {
            closeRamp(now)
            mutable.value = s.copy(phase = WorkoutPhase.COMPLETED, stepElapsedMs = 0, nextStep = null, nextWatts = null)
            return
        }
        val next = definition.step(index + 1, s.ftpSnapshot, profile)
        val target = workoutWatts(step.target, s.ftpSnapshot, profile, elapsed.toDouble() / (step.durationSeconds * 1000L))
        mutable.value = s.copy(stepIndex = index, step = step, stepElapsedMs = elapsed,
            targetWatts = target, nextStep = next, nextWatts = next?.let { workoutWatts(it.target, s.ftpSnapshot, profile) })
        if (step.type == WorkoutStepType.RAMP && definition.rampTest && rampStart == null) rampStart = now - rideOrigin
        if (step.type != WorkoutStepType.RAMP) closeRamp(now)
        dispatch(target, now, force)
    }
    private fun dispatch(target: Int?, now: Long, force: Boolean = false) {
        if (target == null || !force && target == lastSent) return
        lastCommandAt = now
        if (send(target)) lastSent = target else controlFailed(now)
    }
    fun tick(now: Long) {
        val s = state.value
        if (s.phase != WorkoutPhase.ACTIVE || now < lastTick) return
        val delta = now - lastTick; lastTick = now
        var elapsed = s.stepElapsedMs + delta
        var index = s.stepIndex
        var step = s.step ?: return
        var ended = false
        while (elapsed >= step.durationSeconds * 1000L) {
            elapsed -= step.durationSeconds * 1000L; index++
            val next = s.definition!!.step(index, s.ftpSnapshot, profile)
            if (next == null) { ended = true; break }
            step = next
        }
        mutable.value = s.copy(elapsedMs = s.elapsedMs + delta - if (ended) elapsed else 0)
        if (index != s.stepIndex) enter(index, elapsed, now)
        else {
            val watts = workoutWatts(step.target, s.ftpSnapshot, profile, elapsed.toDouble() / (step.durationSeconds * 1000L))
            mutable.value = state.value.copy(stepElapsedMs = elapsed, targetWatts = watts)
            // Optional continuous ramps: change only when watts change, at most once per 10s.
            if (step.target is WorkoutTarget.Ramp && (lastCommandAt == Long.MIN_VALUE || now - lastCommandAt >= 10000))
                dispatch(watts, now)
        }
    }
    fun pause(now: Long, issue: WorkoutIssue? = null) {
        if (state.value.phase != WorkoutPhase.ACTIVE) return
        // Freeze at the last authoritative tick. No new target is emitted while pausing.
        val s = state.value
        val advance = (now - lastTick).coerceAtLeast(0).coerceAtMost(s.remainingMs)
        closeRamp(now); lastTick = now
        mutable.value = s.copy(phase = WorkoutPhase.PAUSED, issue = issue,
            stepElapsedMs = s.stepElapsedMs + advance, elapsedMs = s.elapsedMs + advance)
    }
    fun unavailable(now: Long) = pause(now, WorkoutIssue.TRAINER_UNAVAILABLE)
    fun controlFailed(now: Long) = pause(now, WorkoutIssue.CONTROL_FAILED)
    fun resume(now: Long, trainerReady: Boolean) {
        if (state.value.phase != WorkoutPhase.PAUSED || !trainerReady) return
        lastTick = now
        mutable.value = state.value.copy(phase = WorkoutPhase.ACTIVE, issue = null)
        val s = state.value
        if (s.remainingMs == 0L) enter(s.stepIndex + 1, 0, now, force = true)
        else enter(s.stepIndex, s.stepElapsedMs, now, force = true)
    }
    fun skip(now: Long, trainerReady: Boolean) {
        if (state.value.phase != WorkoutPhase.ACTIVE || !trainerReady) return
        val s = state.value
        mutable.value = s.copy(elapsedMs = s.elapsedMs + (now - lastTick).coerceIn(0, s.remainingMs))
        closeRamp(now); lastTick = now
        enter(state.value.stepIndex + 1, 0, now)
    }
    fun finish(now: Long, testEnded: Boolean = false) {
        if (!state.value.controlsTrainer) return
        if (state.value.phase == WorkoutPhase.ACTIVE) pause(now)
        closeRamp(now)
        mutable.value = state.value.copy(phase = if (testEnded && state.value.definition?.rampTest == true)
            WorkoutPhase.COMPLETED else WorkoutPhase.CANCELLED, issue = null)
    }
    private fun closeRamp(now: Long) {
        rampStart?.let { start ->
            val end = (now - rideOrigin).coerceAtLeast(start)
            if (end > start) ranges.add(EffortRange(start, end))
        }
        rampStart = null
    }
}

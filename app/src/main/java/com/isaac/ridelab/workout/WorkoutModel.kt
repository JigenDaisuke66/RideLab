package com.isaac.ridelab.workout

import com.isaac.ridelab.ble.fec.TrainerErgProfile
import kotlin.math.roundToInt

enum class WorkoutId { RECOVERY_30, ENDURANCE_45, SWEET_SPOT, FTP_RAMP }
enum class WorkoutStepType { WARMUP, WORK, RECOVERY, COOLDOWN, RAMP, FREE_RIDE }
sealed interface WorkoutTarget {
    data class FtpPercent(val fraction: Double) : WorkoutTarget
    data class AbsolutePower(val watts: Int) : WorkoutTarget
    data class Ramp(val startFraction: Double, val endFraction: Double) : WorkoutTarget
    data object FreeRide : WorkoutTarget
}
data class WorkoutStep(val type: WorkoutStepType, val durationSeconds: Int, val target: WorkoutTarget) {
    init { require(durationSeconds > 0) }
}
data class WorkoutDefinition(val id: WorkoutId, val steps: List<WorkoutStep>, val rampTest: Boolean = false) {
    init { require(steps.isNotEmpty()) }
    val requiresFtp: Boolean get() = rampTest || steps.any { it.target is WorkoutTarget.FtpPercent || it.target is WorkoutTarget.Ramp }
    fun step(index: Int, ftp: Int?, profile: TrainerErgProfile): WorkoutStep? {
        if (index < 0) return null
        steps.getOrNull(index)?.let { return it }
        if (!rampTest || ftp == null) return null
        val stage = index - steps.size
        // Visit the clamped ceiling once, then finish; never hold at maximum indefinitely.
        if (stage > 0 && (ftp * (1 + .06 * (stage - 1))).roundToInt() >= profile.maxErgWatts) return null
        return WorkoutStep(WorkoutStepType.RAMP, 60,
            WorkoutTarget.AbsolutePower(profile.clamp((ftp * (1 + .06 * stage)).roundToInt())))
    }
}

object BuiltInWorkouts {
    private fun step(type: WorkoutStepType, minutes: Int, fraction: Double) =
        WorkoutStep(type, minutes * 60, WorkoutTarget.FtpPercent(fraction))
    val all = listOf(
        WorkoutDefinition(WorkoutId.RECOVERY_30, listOf(step(WorkoutStepType.RECOVERY, 30, .50))),
        WorkoutDefinition(WorkoutId.ENDURANCE_45, listOf(step(WorkoutStepType.WARMUP, 5, .50),
            step(WorkoutStepType.WORK, 35, .65), step(WorkoutStepType.COOLDOWN, 5, .45))),
        WorkoutDefinition(WorkoutId.SWEET_SPOT, buildList {
            add(step(WorkoutStepType.WARMUP, 5, .50)); add(step(WorkoutStepType.WARMUP, 5, .65))
            repeat(3) { index ->
                add(step(WorkoutStepType.WORK, 10, .90))
                if (index < 2) add(step(WorkoutStepType.RECOVERY, 5, .55))
            }
            add(step(WorkoutStepType.COOLDOWN, 5, .45))
        }),
        WorkoutDefinition(WorkoutId.FTP_RAMP, listOf(step(WorkoutStepType.WARMUP, 5, .50),
            step(WorkoutStepType.WARMUP, 5, .60)), rampTest = true),
    )
    fun find(id: WorkoutId) = all.first { it.id == id }
}

/** Nearest whole watt, then clamp to device capabilities; no protocol limits here. */
fun workoutWatts(target: WorkoutTarget, ftp: Int?, profile: TrainerErgProfile, progress: Double = 0.0): Int? {
    val value = when (target) {
        is WorkoutTarget.FtpPercent -> {
            require(target.fraction.isFinite() && target.fraction >= 0)
            require(ftp != null && ftp > 0); ftp * target.fraction
        }
        is WorkoutTarget.AbsolutePower -> { require(target.watts >= 0); target.watts.toDouble() }
        is WorkoutTarget.Ramp -> {
            require(ftp != null && ftp > 0)
            require(target.startFraction.isFinite() && target.endFraction.isFinite() &&
                target.startFraction >= 0 && target.endFraction >= 0 && progress.isFinite())
            ftp * (target.startFraction + (target.endFraction - target.startFraction) * progress.coerceIn(0.0, 1.0))
        }
        WorkoutTarget.FreeRide -> return null
    }
    require(value.isFinite())
    return value.coerceIn(profile.minErgWatts.toDouble(), profile.maxErgWatts.toDouble()).roundToInt()
}

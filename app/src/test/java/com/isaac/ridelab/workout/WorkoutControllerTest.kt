package com.isaac.ridelab.workout

import com.isaac.ridelab.ble.fec.ThinkRiderX2MaxProfile
import org.junit.Assert.*
import org.junit.Test

class WorkoutControllerTest {
    @Test fun initialFtpTestStartsWithoutFtpAndUsesFixedTargets() {
        val def = BuiltInWorkouts.find(WorkoutId.INITIAL_FTP_RAMP)
        assertFalse(def.requiresFtp)
        val sent = mutableListOf<Int>()
        val e = start(def, sent, ftp = null)
        assertNull(e.state.value.ftpSnapshot)
        assertEquals(listOf(50), sent)
        e.tick(300_000) // Warmup excluded; first ramp step remains 50 W.
        assertEquals(WorkoutStepType.RAMP, e.state.value.step?.type)
        e.tick(360_000); e.tick(420_000)
        assertEquals(listOf(50, 60, 70), sent)
        e.finish(480_000, true)
        assertEquals(listOf(EffortRange(300_000, 480_000)), e.effortRanges())
        assertEquals(WorkoutPhase.COMPLETED, e.state.value.phase)
    }

    @Test fun initialTestDoesNotDependOnAnExistingFtpAndVisitsCeilingOnce() {
        val def = BuiltInWorkouts.find(WorkoutId.INITIAL_FTP_RAMP)
        assertEquals(listOf(50, 60, 70, 80), (1..4).map { workoutWatts(def.step(it, null, profile)!!.target, null, profile) })
        assertEquals(def.step(12, null, profile), def.step(12, 300, profile))
        val targets = generateSequence(1) { it + 1 }.map { def.step(it, null, profile) }
            .takeWhile { it != null }.map { workoutWatts(it!!.target, null, profile)!! }.toList()
        assertEquals(50, targets.first()); assertEquals(2000, targets.last())
        assertEquals(1, targets.count { it == 2000 })
    }

    @Test fun initialTestPauseDoesNotAdvanceOrSendNewTarget() {
        val sent = mutableListOf<Int>()
        val e = start(BuiltInWorkouts.find(WorkoutId.INITIAL_FTP_RAMP), sent, ftp = null)
        e.tick(360_000); e.pause(370_000); e.tick(900_000)
        assertEquals(listOf(50, 60), sent)
        assertEquals(10_000L, e.state.value.stepElapsedMs)
        e.resume(900_000, true); e.tick(950_000)
        assertEquals(listOf(50, 60, 60, 70), sent)
    }
    @Test fun ridePauseClockKeepsWorkoutAndRecordingElapsedAligned() {
        val ride = com.isaac.ridelab.service.RideSession {}
        ride.begin(); ride.activate(1000)
        val sent = mutableListOf<Int>()
        val engine = start(definition(step(10, 100), step(10, 150)), sent, now = 1000)
        engine.tick(ride.state.value.recordingClock(3000))
        ride.pause(3500); engine.pause(ride.state.value.recordingClock(3500))
        engine.tick(ride.state.value.recordingClock(303_500))
        assertEquals(listOf(100), sent)
        assertEquals(2500L, engine.state.value.elapsedMs)
        ride.resume(303_500); engine.resume(ride.state.value.recordingClock(303_500), true)
        engine.tick(ride.state.value.recordingClock(304_500))
        assertEquals(3500L, engine.state.value.elapsedMs)
        assertEquals(ride.state.value.elapsedMs(304_500), engine.state.value.elapsedMs)
        assertEquals(listOf(100, 100), sent)
    }
    private val profile = ThinkRiderX2MaxProfile
    private fun step(seconds: Int, watts: Int) = WorkoutStep(WorkoutStepType.WORK, seconds, WorkoutTarget.AbsolutePower(watts))
    private fun definition(vararg steps: WorkoutStep) = WorkoutDefinition(WorkoutId.ENDURANCE_45, steps.toList())
    private fun start(definition: WorkoutDefinition, sent: MutableList<Int>, ftp: Int? = 150,
        now: Long = 0) = WorkoutController(profile) { sent.add(it); true }.apply {
        assertTrue(prepare(definition)); assertTrue(start(ftp, now, now))
    }

    @Test fun percentageRoundingAndCapabilityClamping() {
        assertEquals(75, workoutWatts(WorkoutTarget.FtpPercent(.5), 150, profile))
        assertEquals(135, workoutWatts(WorkoutTarget.FtpPercent(.9), 150, profile))
        assertEquals(98, workoutWatts(WorkoutTarget.FtpPercent(.65), 150, profile))
        assertEquals(50, workoutWatts(WorkoutTarget.FtpPercent(.5), 50, profile))
        assertEquals(2000, workoutWatts(WorkoutTarget.AbsolutePower(3000), null, profile))
        assertNull(workoutWatts(WorkoutTarget.FreeRide, null, profile))
    }
    @Test fun invalidTargetsAreRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { workoutWatts(WorkoutTarget.FtpPercent(bad), 150, profile) }
        }
        assertThrows(IllegalArgumentException::class.java) { workoutWatts(WorkoutTarget.AbsolutePower(-1), 150, profile) }
    }
    @Test fun preparingDoesNotSendAndMissingFtpRefusesStart() {
        val sent = mutableListOf<Int>()
        val engine = WorkoutController(profile) { sent.add(it); true }
        engine.prepare(BuiltInWorkouts.find(WorkoutId.RECOVERY_30))
        assertTrue(sent.isEmpty())
        assertFalse(engine.start(null, 0, 0))
        assertEquals(WorkoutIssue.FTP_REQUIRED, engine.state.value.issue)
        assertTrue(sent.isEmpty())
    }
    @Test fun firstStepAndExactlyOneTransitionDespiteRepeatedTicks() {
        val sent = mutableListOf<Int>()
        val e = start(definition(step(1, 100), step(1, 150)), sent)
        assertEquals(listOf(100), sent)
        repeat(3) { e.tick(500) }
        assertEquals(500L, e.state.value.stepElapsedMs)
        repeat(3) { e.tick(1000) }
        assertEquals(listOf(100, 150), sent)
        assertEquals(1, e.state.value.stepIndex)
        e.tick(2000); e.tick(9000)
        assertEquals(WorkoutPhase.COMPLETED, e.state.value.phase)
        assertEquals(2000L, e.state.value.elapsedMs)
        assertEquals(listOf(100, 150), sent)
    }
    @Test fun pauseFreezesAndResumePreservesRemaining() {
        val sent = mutableListOf<Int>()
        val e = start(definition(step(10, 100), step(10, 150)), sent)
        e.tick(2000); e.pause(2500); e.tick(80000)
        assertEquals(2500L, e.state.value.stepElapsedMs)
        assertEquals(7500L, e.state.value.remainingMs)
        assertEquals(listOf(100), sent)
        e.resume(80000, true); e.tick(81000)
        assertEquals(3500L, e.state.value.stepElapsedMs)
        assertEquals(listOf(100, 100), sent)
    }
    @Test fun skipResetsTimerAndFinalSkipCompletes() {
        val sent = mutableListOf<Int>()
        val e = start(definition(step(10, 100), step(10, 150)), sent)
        e.skip(3000, true)
        assertEquals(0L, e.state.value.stepElapsedMs)
        assertEquals(listOf(100, 150), sent)
        e.skip(4000, true)
        assertEquals(WorkoutPhase.COMPLETED, e.state.value.phase)
        assertEquals(4000L, e.state.value.elapsedMs)
    }
    @Test fun cancellingStopsAllProgressAndCommands() {
        val sent = mutableListOf<Int>()
        val e = start(definition(step(1, 100), step(1, 150)), sent)
        e.finish(500); e.tick(50000); e.resume(50000, true); e.skip(50000, true)
        assertEquals(WorkoutPhase.CANCELLED, e.state.value.phase)
        assertEquals(listOf(100), sent)
    }
    @Test fun lateTickSkipsExpiredTargetsAndBackwardClockDoesNothing() {
        val sent = mutableListOf<Int>()
        val e = start(definition(step(1, 100), step(1, 150), step(2, 200)), sent)
        e.tick(2500); e.tick(2400)
        assertEquals(listOf(100, 200), sent)
        assertEquals(500L, e.state.value.stepElapsedMs)
    }
    @Test fun equalTargetsAreNotResentEverySecondOrTransition() {
        val sent = mutableListOf<Int>()
        val e = start(definition(step(10, 100), step(10, 100)), sent)
        (1..19).forEach { e.tick(it * 1000L) }
        assertEquals(listOf(100), sent)
    }
    @Test fun disconnectRequiresExplicitResumeAndDoesNotCatchUp() {
        val sent = mutableListOf<Int>()
        val e = start(definition(step(10, 100), step(10, 150)), sent)
        e.tick(2000); e.unavailable(2000); e.tick(90000); e.resume(90000, false)
        assertEquals(WorkoutPhase.PAUSED, e.state.value.phase)
        e.resume(90000, true); e.tick(91000)
        assertEquals(0, e.state.value.stepIndex)
        assertEquals(3000L, e.state.value.stepElapsedMs)
        assertEquals(listOf(100, 100), sent)
    }
    @Test fun resumeAtBoundaryNeverSendsExpiredStep() {
        val sent = mutableListOf<Int>()
        val e = start(definition(step(1, 100), step(2, 150)), sent)
        e.unavailable(1000); e.resume(20000, true)
        assertEquals(listOf(100, 150), sent)
        assertEquals(1, e.state.value.stepIndex)
    }
    @Test fun controlFailurePausesWithoutUncontrolledRetry() {
        val sent = mutableListOf<Int>()
        val e = WorkoutController(profile) { sent.add(it); false }
        e.prepare(definition(step(5, 100)))
        e.start(null, 0, 0)
        (1..100).forEach { e.tick(it * 1000L) }
        assertEquals(WorkoutPhase.PAUSED, e.state.value.phase)
        assertEquals(WorkoutIssue.CONTROL_FAILED, e.state.value.issue)
        assertEquals(listOf(100), sent)
    }
    @Test fun asynchronousFailureAlsoFreezes() {
        val sent = mutableListOf<Int>()
        val e = start(definition(step(5, 100), step(5, 150)), sent)
        e.controlFailed(500); e.tick(100000)
        assertEquals(WorkoutIssue.CONTROL_FAILED, e.state.value.issue)
        assertEquals(listOf(100), sent)
    }
    @Test fun ftpSnapshotIsUsedForEveryStep() {
        val sent = mutableListOf<Int>()
        val def = definition(WorkoutStep(WorkoutStepType.WORK, 1, WorkoutTarget.FtpPercent(.5)),
            WorkoutStep(WorkoutStepType.WORK, 1, WorkoutTarget.FtpPercent(.9)))
        val e = start(def, sent, 150)
        e.tick(1000)
        assertEquals(150, e.state.value.ftpSnapshot)
        assertEquals(listOf(75, 135), sent)
    }
    @Test fun rampGeneratedFromOriginalFtpWithSixPercentIncrements() {
        val def = BuiltInWorkouts.find(WorkoutId.FTP_RAMP)
        assertEquals(listOf(150, 159, 168, 177), (2..5).map { workoutWatts(def.step(it, 150, profile)!!.target, 150, profile) })
        var index = 2
        val values = mutableListOf<Int>()
        while (true) {
            val step = def.step(index++, 150, profile) ?: break
            values.add(workoutWatts(step.target, 150, profile)!!)
        }
        assertEquals(2000, values.last())
        assertEquals(1, values.count { it == 2000 })
    }
    @Test fun rampEffortExcludesWarmupAndSeparatesPause() {
        val sent = mutableListOf<Int>()
        val e = start(BuiltInWorkouts.find(WorkoutId.FTP_RAMP), sent)
        e.tick(300000); e.tick(600000); e.tick(610000); e.pause(610000)
        assertEquals(listOf(EffortRange(600000, 610000)), e.effortRanges())
        e.resume(620000, true); e.finish(630000, true)
        assertEquals(listOf(EffortRange(600000, 610000), EffortRange(620000, 630000)), e.effortRanges())
        assertEquals(WorkoutPhase.COMPLETED, e.state.value.phase)
    }
    @Test fun builtInDurationsAndNoDuplicateControllerPreparation() {
        assertEquals(listOf(30, 45, 55), BuiltInWorkouts.all.take(3).map { it.steps.sumOf { s -> s.durationSeconds } / 60 })
        val e = WorkoutController(profile) { true }
        assertTrue(e.prepare(BuiltInWorkouts.all.first()))
        assertFalse(e.prepare(BuiltInWorkouts.all.last()))
    }
    @Test fun optionalLinearRampRateLimitsRequests() {
        val sent = mutableListOf<Int>()
        val e = start(definition(WorkoutStep(WorkoutStepType.WARMUP, 60, WorkoutTarget.Ramp(.5, 1.0))), sent, 100)
        (1..9).forEach { e.tick(it * 1000L) }
        assertEquals(listOf(50), sent)
        e.tick(10000)
        assertEquals(listOf(50, 58), sent)
    }
}

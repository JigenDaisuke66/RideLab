package com.isaac.ridelab.service

import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.data.model.RideSample
import com.isaac.ridelab.data.model.RideSession as StoredSession
import com.isaac.ridelab.data.model.StoredRideStatus
import com.isaac.ridelab.data.repository.RideRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RideRecorderTest {
    @Test fun repeatedStartAndUiRecreationUseExactlyOneSession() = runBlocking {
        val repo = MemoryRepository()
        val recorder = RideRecorder(repo)
        val id = recorder.start(StoredSession(startTimeEpochMillis = 10_000), 100)
        val lifecycle = RideSession { error("Active UI teardown must not disconnect") }
        lifecycle.begin(); lifecycle.activate(100, id)
        repeat(3) {
            lifecycle.uiHidden()
            assertEquals(id, recorder.start(StoredSession(startTimeEpochMillis = 20_000), 200))
        }
        assertEquals(1, repo.sessions.size)
        assertEquals(id, lifecycle.state.value.sessionId)
        recorder.sample(LiveRideData(), 11_000, 1100)
        assertEquals(id, repo.samples.single().sessionId)
    }

    @Test fun samplingIsLimitedToOnePerSecondAndNullsRemainNull() = runBlocking {
        val repo = MemoryRepository(); val recorder = RideRecorder(repo)
        recorder.start(StoredSession(startTimeEpochMillis = 10_000), 100)
        assertTrue(recorder.sample(LiveRideData(), 10_000, 100))
        assertFalse(recorder.sample(LiveRideData(trainerPowerWatts = 999), 10_050, 150))
        assertTrue(recorder.sample(LiveRideData(trainerPowerWatts = 0), 11_000, 1100))
        assertEquals(2, repo.samples.size)
        with(repo.samples.first()) {
            assertNull(trainerPowerWatts); assertNull(cadenceRpm); assertNull(heartRateBpm)
            assertNull(targetPowerWatts); assertNull(externalPowerWatts); assertNull(speedKph); assertNull(resistanceLevel)
        }
        assertEquals(0, repo.samples.last().trainerPowerWatts)
    }

    @Test fun completionAddsLastSampleAndFinalizesSameIdWithMonotonicDuration() = runBlocking {
        val repo = MemoryRepository(); val recorder = RideRecorder(repo)
        val id = recorder.start(StoredSession(startTimeEpochMillis = 10_000), 100)
        recorder.sample(LiveRideData(trainerPowerWatts = 102, heartRateBpm = 134), 11_000, 1100)
        assertEquals(id, recorder.complete(LiveRideData(targetPowerWatts = 100), 9_000, 2600)) // Clock moved backwards.
        val saved = repo.sessions.getValue(id)
        assertEquals(StoredRideStatus.COMPLETED, saved.status)
        assertEquals(2500L, saved.durationMillis)
        assertEquals(9000L, saved.endTimeEpochMillis)
        assertEquals(100, saved.lastTargetPowerWatts)
        assertEquals(listOf(1000L, 2500L), repo.samples.map { it.elapsedMillis })
        assertNull(recorder.complete(LiveRideData(), 10_000, 3000))
        assertEquals(2, repo.samples.size)
    }

    @Test fun endAtLastTimestampDoesNotDuplicateSample() = runBlocking {
        val repo = MemoryRepository(); val recorder = RideRecorder(repo)
        recorder.start(StoredSession(startTimeEpochMillis = 10_000), 100)
        recorder.sample(LiveRideData(), 10_000, 100)
        recorder.complete(LiveRideData(), 10_000, 100)
        assertEquals(1, repo.samples.size)
    }

    @Test fun nextRideUsesNewIdAndCannotAppendAfterEnd() = runBlocking {
        val repo = MemoryRepository(); val recorder = RideRecorder(repo)
        val first = recorder.start(StoredSession(startTimeEpochMillis = 0), 0)
        recorder.complete(LiveRideData(), 100, 100)
        assertFalse(recorder.sample(LiveRideData(), 200, 200))
        val second = recorder.start(StoredSession(startTimeEpochMillis = 1000), 1000)
        recorder.sample(LiveRideData(), 1100, 1100)
        assertNotEquals(first, second)
        assertEquals(second, repo.samples.last().sessionId)
    }

    @Test fun interruptedRecordingIsAbortedAtLastSavedProgressNotRestartTime() = runBlocking {
        val repo = MemoryRepository(); val old = RideRecorder(repo)
        val id = old.start(StoredSession(startTimeEpochMillis = 1000), 100)
        old.sample(LiveRideData(), 3000, 2100)
        RideRecorder(repo).start(StoredSession(startTimeEpochMillis = 999_000), 0)
        val saved = repo.sessions.getValue(id)
        assertEquals(StoredRideStatus.ABORTED, saved.status)
        assertEquals(2000L, saved.durationMillis)
        assertEquals(3000L, saved.endTimeEpochMillis)
    }

    @Test fun detectedDestructionAbortsInsteadOfCompleting() = runBlocking {
        val repo = MemoryRepository(); val recorder = RideRecorder(repo)
        val id = recorder.start(StoredSession(startTimeEpochMillis = 10_000), 100)
        recorder.sample(LiveRideData(), 11_000, 1100)
        recorder.abort()
        assertEquals(StoredRideStatus.ABORTED, repo.sessions.getValue(id).status)
    }

    @Test fun failedCompletionNeverClaimsCompleted() = runBlocking {
        val repo = MemoryRepository(); val recorder = RideRecorder(repo)
        val id = recorder.start(StoredSession(startTimeEpochMillis = 10_000), 100)
        recorder.sample(LiveRideData(), 11_000, 1100)
        repo.failCompletion = true
        try { recorder.complete(LiveRideData(), 12_000, 2100); fail("Expected storage error") }
        catch (_: IllegalStateException) { }
        assertEquals(StoredRideStatus.ABORTED, repo.sessions.getValue(id).status)
        assertEquals(1, repo.samples.size)
    }

    @Test fun cancellationWaitsForInFlightInsertBeforeFinalization() = runBlocking {
        val repo = MemoryRepository(); val recorder = RideRecorder(repo)
        recorder.start(StoredSession(startTimeEpochMillis = 10_000), 100)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        repo.beforeAppend = { entered.complete(Unit); release.await() }
        val insert = launch { recorder.sample(LiveRideData(), 11_000, 1100) }
        entered.await(); insert.cancel()
        val finish = async { recorder.complete(LiveRideData(), 12_000, 2100) }
        yield(); assertFalse(finish.isCompleted)
        release.complete(Unit); insert.join(); finish.await()
        assertEquals(listOf(1000L, 2000L), repo.samples.map { it.elapsedMillis })
        assertEquals(StoredRideStatus.COMPLETED, repo.sessions.values.single().status)
    }

    private class MemoryRepository : RideRepository {
        val sessions = mutableMapOf<Long, StoredSession>()
        val samples = mutableListOf<RideSample>()
        var failCompletion = false
        var beforeAppend: (suspend () -> Unit)? = null
        override suspend fun recoverInterrupted(): Int {
            val active = sessions.values.filter { it.status == StoredRideStatus.ACTIVE }
            active.forEach { session ->
                sessions[session.id] = session.copy(status = StoredRideStatus.ABORTED, activeSlot = null,
                    endTimeEpochMillis = samples.lastOrNull { it.sessionId == session.id }?.timestampEpochMillis ?: session.startTimeEpochMillis)
            }
            return active.size
        }
        override suspend fun start(session: StoredSession): Long {
            check(sessions.values.none { it.status == StoredRideStatus.ACTIVE })
            val id = sessions.size.toLong() + 1; sessions[id] = session.copy(id = id); return id
        }
        override suspend fun append(sample: RideSample) {
            beforeAppend?.invoke()
            check(sessions.getValue(sample.sessionId).status == StoredRideStatus.ACTIVE)
            samples.add(sample)
            val session = sessions.getValue(sample.sessionId)
            sessions[sample.sessionId] = session.copy(durationMillis = sample.elapsedMillis,
                lastTargetPowerWatts = sample.targetPowerWatts ?: session.lastTargetPowerWatts)
        }
        override suspend fun finish(id: Long, finalSample: RideSample?, end: Long, duration: Long, status: StoredRideStatus, target: Int?) {
            if (failCompletion && status == StoredRideStatus.COMPLETED) error("Disk write failed")
            if (finalSample != null) append(finalSample)
            sessions[id] = sessions.getValue(id).copy(endTimeEpochMillis = end, durationMillis = duration,
                status = status, activeSlot = null, lastTargetPowerWatts = target)
        }
    }
}

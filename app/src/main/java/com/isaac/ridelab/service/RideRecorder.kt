package com.isaac.ridelab.service

import com.isaac.ridelab.data.model.LiveRideData
import com.isaac.ridelab.data.model.RideSession as StoredSession
import com.isaac.ridelab.data.model.StoredRideStatus
import com.isaac.ridelab.data.repository.RideRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Service-owned serialized recorder; no UI or Bluetooth callbacks perform inserts.
 * Each insert is committed directly (no memory buffer). Cancellation cannot abandon
 * an in-flight commit whose result is needed by the finalizer.
 */
class RideRecorder(private val repository: RideRepository) {
    private val mutex = Mutex()
    private data class Active(val id: Long, val epoch: Long, val monotonic: Long,
        var lastElapsed: Long = -1, var lastEpoch: Long = epoch, var lastTarget: Int? = null)
    private var active: Active? = null

    suspend fun start(metadata: StoredSession, monotonicMs: Long): Long = mutex.withLock {
        withContext(NonCancellable) {
            active?.id ?: run {
                repository.recoverInterrupted()
                val id = repository.start(metadata)
                active = Active(id, metadata.startTimeEpochMillis, monotonicMs)
                id
            }
        }
    }

    suspend fun sample(data: LiveRideData, epochMs: Long, monotonicMs: Long): Boolean = mutex.withLock {
        withContext(NonCancellable) {
            val current = active ?: return@withContext false
            val elapsed = (monotonicMs - current.monotonic).coerceAtLeast(0)
            if (current.lastElapsed >= 0 && elapsed / 1_000 <= current.lastElapsed / 1_000) return@withContext false
            repository.append(data.sample(current.id, epochMs, elapsed))
            current.lastElapsed = elapsed; current.lastEpoch = epochMs
            current.lastTarget = data.targetPowerWatts ?: current.lastTarget
            true
        }
    }

    suspend fun complete(data: LiveRideData, epochMs: Long, monotonicMs: Long): Long? = mutex.withLock {
        withContext(NonCancellable) {
            val current = active ?: return@withContext null
            val duration = (monotonicMs - current.monotonic).coerceAtLeast(current.lastElapsed.coerceAtLeast(0))
            val finalSample = if (duration > current.lastElapsed) data.sample(current.id, epochMs, duration) else null
            try {
                repository.finish(current.id, finalSample, epochMs, duration, StoredRideStatus.COMPLETED,
                    data.targetPowerWatts ?: current.lastTarget)
                current.id
            } catch (error: Exception) {
                // Never leave a failed completion looking successful. Recovery also covers disk failure.
                try { abortCurrent(current) } catch (abortError: Exception) { error.addSuppressed(abortError) }
                throw error
            } finally { active = null }
        }
    }

    suspend fun abort(): Unit = mutex.withLock {
        withContext(NonCancellable) {
            val current = active ?: return@withContext
            try { abortCurrent(current) } finally { active = null }
        }
    }

    private suspend fun abortCurrent(current: Active) = repository.finish(current.id, null,
        current.lastEpoch, current.lastElapsed.coerceAtLeast(0), StoredRideStatus.ABORTED, current.lastTarget)
}

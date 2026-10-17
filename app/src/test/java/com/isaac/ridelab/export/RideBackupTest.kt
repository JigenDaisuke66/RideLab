package com.isaac.ridelab.export

import com.isaac.ridelab.data.model.*
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class RideBackupTest {
    private fun ride() = BackupRide(
        RideSession(id = 42, startTimeEpochMillis = 1000, endTimeEpochMillis = 5000,
            durationMillis = 3000, status = StoredRideStatus.COMPLETED, activeSlot = null,
            trainerName = "骑行台", trainerAddress = "AA", heartRateSensorName = "HR",
            heartRateSensorAddress = "BB", lastTargetPowerWatts = 150,
            analyticsFtpWatts = 130, bodyWeightKg = 72.5),
        listOf(RideSample(42, 1000, 0, 0, 0.0, null, 150, 10, 20.5, 3.0, "AA", "BB"),
            RideSample(42, 4000, 3000, null, null, 120, null)))

    private fun encode(rides: List<BackupRide>): ByteArray =
        ByteArrayOutputStream().also { RideBackup.write(rides, it) }.toByteArray()

    @Test fun roundTripPreservesEveryFieldAndNullsAndZero() {
        val original = ride()
        val decoded = RideBackup.read(ByteArrayInputStream(encode(listOf(original)))).single()
        assertTrue(RideBackup.same(original, decoded))
        assertEquals(0, decoded.samples.first().trainerPowerWatts)
        assertNull(decoded.samples.last().trainerPowerWatts)
        assertEquals(130, decoded.session.analyticsFtpWatts)
        assertEquals(72.5, decoded.session.bodyWeightKg!!, 0.0)
    }
    @Test fun interruptedAndEmptyHistoryRoundTrip() {
        assertTrue(RideBackup.read(ByteArrayInputStream(encode(emptyList()))).isEmpty())
        val source = ride().let { it.copy(session = it.session.copy(status = StoredRideStatus.ABORTED,
            analyticsFtpWatts = null, bodyWeightKg = null), samples = emptyList()) }
        assertTrue(RideBackup.same(source, RideBackup.read(ByteArrayInputStream(encode(listOf(source)))).single()))
    }
    @Test fun duplicateComparisonIgnoresOnlyLocalIds() {
        val original = ride()
        assertTrue(RideBackup.same(original, original.copy(session = original.session.copy(id = 7),
            samples = original.samples.map { it.copy(sessionId = 7) })))
        assertFalse(RideBackup.same(original, original.copy(session = original.session.copy(analyticsFtpWatts = 170))))
        assertFalse(RideBackup.same(original, original.copy(samples = original.samples.dropLast(1))))
    }
    @Test fun damagedAndTruncatedFilesFailBeforeRestore() {
        val bytes = encode(listOf(ride()))
        for (bad in listOf(bytes.copyOf(bytes.size / 2), ByteArray(20), bytes.copyOf().also { it[it.size - 12] = 0 })) {
            assertThrows(Exception::class.java) { RideBackup.read(ByteArrayInputStream(bad)) }
        }
    }
    @Test fun activeAndInvalidSampleOrderingRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            encode(listOf(ride().let { it.copy(session = it.session.copy(status = StoredRideStatus.ACTIVE)) }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encode(listOf(ride().let { it.copy(samples = it.samples.reversed()) }))
        }
    }
}

package com.isaac.ridelab.ui.screen

import com.isaac.ridelab.data.model.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoryGroupsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun ride(id: Long, time: String, status: StoredRideStatus = StoredRideStatus.COMPLETED) =
        RideHistoryEntry(RideSession(id = id, startTimeEpochMillis = Instant.parse(time).toEpochMilli(), status = status),
            1, 0, 0, null, null, null, null, null, null)

    @Test fun yearsMonthsAndRidesAreNewestFirst() {
        val rides = listOf(ride(1, "2025-12-01T00:00:00Z"), ride(2, "2026-09-01T00:00:00Z"),
            ride(3, "2026-10-01T00:00:00Z"), ride(4, "2026-10-02T00:00:00Z"))
        val groups = groupRideHistory(rides, zone)
        assertEquals(listOf(2026, 2025), groups.map { it.year })
        assertEquals(listOf(10, 9), groups.first().months.map { it.month })
        assertEquals(listOf(4L, 3L), groups.first().months.first().rides.map { it.session.id })
        assertEquals(3, groups.first().rideCount)
    }

    @Test fun localTimeZoneDeterminesCalendarBoundary() {
        val row = ride(1, "2025-12-31T18:00:00Z")
        assertEquals(2026, groupRideHistory(listOf(row), zone).single().year)
        assertEquals(1, groupRideHistory(listOf(row), zone).single().months.single().month)
        assertEquals(2025, groupRideHistory(listOf(row), ZoneId.of("UTC")).single().year)
    }

    @Test fun activeExcludedInterruptedRetainedAndEmptyWorks() {
        val rows = listOf(ride(1, "2026-10-01T00:00:00Z", StoredRideStatus.ACTIVE),
            ride(2, "2026-10-01T00:00:00Z", StoredRideStatus.ABORTED))
        val group = groupRideHistory(rows, zone).single()
        assertEquals(1, group.rideCount)
        assertEquals(2L, group.months.single().rides.single().session.id)
        assertTrue(groupRideHistory(emptyList(), zone).isEmpty())
    }
}

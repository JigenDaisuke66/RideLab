package com.isaac.ridelab.ui.screen

import com.isaac.ridelab.data.model.RideHistoryEntry
import com.isaac.ridelab.data.model.StoredRideStatus
import java.time.Instant
import java.time.ZoneId

internal data class HistoryMonth(val month: Int, val rides: List<RideHistoryEntry>)
internal data class HistoryYear(val year: Int, val months: List<HistoryMonth>) {
    val rideCount: Int get() = months.sumOf { it.rides.size }
}

/** Calendar groups use the same local time zone as the displayed ride start time. */
internal fun groupRideHistory(rides: List<RideHistoryEntry>, zone: ZoneId): List<HistoryYear> =
    rides.filter { it.session.status != StoredRideStatus.ACTIVE }
        .sortedWith(compareByDescending<RideHistoryEntry> { it.session.startTimeEpochMillis }
            .thenByDescending { it.session.id })
        .groupBy { Instant.ofEpochMilli(it.session.startTimeEpochMillis).atZone(zone).year }
        .toSortedMap(compareByDescending { it })
        .map { (year, entries) ->
            HistoryYear(year, entries.groupBy {
                Instant.ofEpochMilli(it.session.startTimeEpochMillis).atZone(zone).monthValue
            }.toSortedMap(compareByDescending { it })
                .map { (month, rows) -> HistoryMonth(month, rows) })
        }

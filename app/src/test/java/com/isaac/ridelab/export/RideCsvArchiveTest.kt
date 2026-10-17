package com.isaac.ridelab.export

import com.isaac.ridelab.data.model.*
import java.io.*
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test

class RideCsvArchiveTest {
    private val labels = CsvArchiveLabels(List(12) { "采样$it" }, List(6) { "汇总$it" }, "完成", "中断")
    private fun archive(rides: List<BackupRide>): Map<String, String> {
        val bytes = ByteArrayOutputStream()
        RideCsvArchive.write(rides, bytes, labels)
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return entries
    }
    private fun ride(start: Long) = BackupRide(RideSession(startTimeEpochMillis = start,
        durationMillis = 1000, status = StoredRideStatus.COMPLETED, activeSlot = null),
        listOf(RideSample(0, start, 0, 0, null, null, null)))

    @Test fun zipContainsSummaryAndEveryRideWithUniqueNamesAndNewestFirst() {
        val result = archive(listOf(ride(1000), ride(2000), ride(2000)))
        assertEquals(4, result.size)
        assertTrue(result.keys.first() == "summary.csv")
        val summary = result.getValue("summary.csv")
        assertTrue(summary.startsWith("\uFEFF"))
        assertEquals(4, summary.trimEnd().lines().size)
        assertTrue(result.keys.elementAt(1).contains("00-00-02"))
        assertTrue(result.values.drop(1).all { it.startsWith("\uFEFF") && it.contains(",0,0,,,,") })
    }
    @Test fun emptyExportStillContainsReadableSummaryHeaders() {
        val result = archive(emptyList())
        assertEquals(setOf("summary.csv"), result.keys)
        assertTrue(result.values.single().contains("汇总0"))
    }
    @Test fun activeRidesRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            archive(listOf(ride(1000).let { it.copy(session = it.session.copy(status = StoredRideStatus.ACTIVE)) }))
        }
    }
}

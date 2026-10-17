package com.isaac.ridelab.export

import com.isaac.ridelab.data.model.StoredRideStatus
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CsvArchiveLabels(val sampleHeaders: List<String>, val summaryHeaders: List<String>,
    val completed: String, val aborted: String)

/** Standard ZIP containing UTF-8/BOM CSV files. Missing values remain empty. */
object RideCsvArchive {
    fun write(rides: List<BackupRide>, output: OutputStream, labels: CsvArchiveLabels,
        checkCancelled: () -> Unit = {}) {
        require(labels.summaryHeaders.size == 6 && labels.sampleHeaders.size == RideCsvExporter.COLUMN_COUNT)
        require(rides.none { it.session.status == StoredRideStatus.ACTIVE })
        val ordered = rides.sortedByDescending { it.session.startTimeEpochMillis }
        val names = ordered.mapIndexed { index, ride ->
            "${index + 1}_${RideCsvExporter.filename(ride.session.startTimeEpochMillis)}"
        }
        ZipOutputStream(output).use { zip ->
            val writer = zip.writer(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry("summary.csv"))
            writer.write("\uFEFF")
            fun row(fields: List<Any?>) {
                writer.write(fields.joinToString(",") { RideCsvExporter.escape(it?.toString() ?: "") } + "\r\n")
            }
            row(labels.summaryHeaders)
            ordered.forEachIndexed { index, ride ->
                checkCancelled()
                val s = ride.session
                row(listOf(names[index], Instant.ofEpochMilli(s.startTimeEpochMillis),
                    s.endTimeEpochMillis?.let(Instant::ofEpochMilli), s.durationMillis,
                    if (s.status == StoredRideStatus.COMPLETED) labels.completed else labels.aborted, ride.samples.size))
            }
            writer.flush(); zip.closeEntry()
            ordered.forEachIndexed { index, ride ->
                checkCancelled()
                zip.putNextEntry(ZipEntry(names[index]))
                RideCsvExporter.write(writer, labels.sampleHeaders, ride.samples, checkCancelled)
                writer.flush(); zip.closeEntry()
            }
        }
    }
}

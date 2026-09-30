package com.isaac.ridelab.export

import com.isaac.ridelab.data.model.RideSample
import java.io.Writer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure serialization. Headers are resolved by the UI, never translated here. */
object RideCsvExporter {
    const val COLUMN_COUNT = 12
    private val filenameTime = DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss", Locale.ROOT)
        .withZone(ZoneOffset.UTC)

    fun filename(startEpochMillis: Long) = "RideLab_${filenameTime.format(Instant.ofEpochMilli(startEpochMillis))}Z.csv"

    fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' })
        "\"" + value.replace("\"", "\"\"") + "\"" else value

    /** UTF-8 BOM is emitted by this writer; caller chooses UTF-8 and owns stream closure.
     * Elapsed order is authoritative even if wall time changes during a ride.
     */
    fun write(writer: Writer, headers: List<String>, samples: List<RideSample>, checkCancelled: () -> Unit = {}) {
        require(headers.size == COLUMN_COUNT)
        writer.write("\uFEFF")
        fun row(fields: List<Any?>) {
            fields.forEachIndexed { index, field ->
                if (index != 0) writer.write(",")
                writer.write(escape(field?.toString() ?: ""))
            }
            writer.write("\r\n")
        }
        row(headers)
        for (sample in samples.sortedBy { it.elapsedMillis }) {
            checkCancelled()
            row(listOf(Instant.ofEpochMilli(sample.timestampEpochMillis).toString(),
                sample.timestampEpochMillis, sample.elapsedMillis, sample.trainerPowerWatts,
                sample.cadenceRpm, sample.heartRateBpm, sample.targetPowerWatts,
                sample.externalPowerWatts, sample.speedKph, sample.resistanceLevel,
                sample.trainerAddress, sample.heartRateSensorAddress))
        }
    }
}

package com.isaac.ridelab.ai.analysis

import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.coroutineContext

/** Streaming per-ride aggregation; raw samples never enter the model payload. */
internal class RideHistorySummaryProvider(private val data: AiRideData) {
    suspend fun collect(now: Long, zoneId: String, question: String): JSONObject {
        val zone = ZoneId.of(zoneId)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val lifetime = Totals()
        val monthly = sortedMapOf<String, Totals>()
        val yearly = sortedMapOf<String, Totals>()
        val periods = listOf(7, 28, 90).associateWith { Totals() }
        val since = periods.keys.associateWith { today.minusDays(it.toLong() - 1).atStartOfDay(zone).toInstant().toEpochMilli() }
        val selection = HistoryScope.select(question, today)
        val selected = Totals()
        val recent = JSONArray()
        val ftpChanges = JSONArray()
        var previousFtp: Int? = null
        var missingFtp = 0
        var metricFailures = 0
        var latest: JSONObject? = null
        var selectedRide: JSONObject? = null
        var first: Long? = null
        var last: Long? = null
        var lastId = 0L
        // A high-water ID prevents newly completed rides from extending the scan indefinitely.
        val high = rows("SELECT MAX(id) AS id FROM ride_sessions WHERE status = 'COMPLETED' AND startTimeEpochMillis <= $now")
            .firstOrNull()?.optLong("id", 0) ?: 0L
        val ftpObservations = sortedMapOf<Pair<Long, Long>, Int?>(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
        while (lastId < high) {
            coroutineContext.ensureActive()
            val page = rows("SELECT id, startTimeEpochMillis, durationMillis, analyticsFtpWatts FROM ride_sessions WHERE status = 'COMPLETED' AND startTimeEpochMillis <= $now AND id > $lastId AND id <= $high ORDER BY id LIMIT 50")
            if (page.isEmpty()) break
            for (session in page) {
                coroutineContext.ensureActive()
                val id = session.getLong("id")
                if (id <= lastId) throw AiAnalysisException(AiAnalysisError.STORAGE)
                lastId = id
                val start = session.getLong("startTimeEpochMillis")
                val duration = session.getLong("durationMillis").coerceAtLeast(0)
                val date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                val ftp = session.number("analyticsFtpWatts")?.toInt()
                ftpObservations[start to id] = ftp
                if (ftp == null) missingFtp++
                val metrics = try { JSONObject(data.metrics(id)) }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (_: Exception) { JSONObject().put("error", "METRICS_READ_FAILED") }
                val valid = !metrics.has("error") && metrics.optLong("session_id") == id
                if (!valid) metricFailures++
                val m = if (valid) metrics else JSONObject()
                lifetime.add(duration, m)
                monthly.getOrPut(date.toString().take(7)) { Totals() }.add(duration, m)
                yearly.getOrPut(date.year.toString()) { Totals() }.add(duration, m)
                periods.forEach { (days, totals) -> if (start >= since.getValue(days)) totals.add(duration, m) }
                if (selection.includes(date)) selected.add(duration, m)
                val detail = JSONObject(m.toString()).put("session_id", id).put("start_epoch_ms", start)
                    .put("duration_ms", duration).put("ride_ftp_w", ftp ?: JSONObject.NULL)
                if (!valid) detail.put("metrics_error", metrics.optString("error", "INVALID_METRICS"))
                if (selection.from != null && selection.includes(date) && start >= (selectedRide?.optLong("start_epoch_ms") ?: Long.MIN_VALUE)) selectedRide = detail
                val candidates = (0 until recent.length()).map { recent.getJSONObject(it) }.plus(detail)
                    .sortedWith(compareByDescending<JSONObject> { it.getLong("start_epoch_ms") }.thenByDescending { it.getLong("session_id") }).take(12)
                while (recent.length() > 0) recent.remove(0)
                candidates.forEach { recent.put(it) }
                latest = candidates.firstOrNull()
                first = minOf(first ?: start, start); last = maxOf(last ?: start, start)
            }
        }
        // Sparse known observations are not an FTP edit log. Missing rides are counted, never backfilled.
        ftpObservations.forEach { (key, value) ->
            if (value != null) {
                if (value != previousFtp) ftpChanges.put(JSONArray().put(key.first).put(value))
                previousFtp = value
            }
        }
        fun buckets(values: Map<String, Totals>) = JSONArray().apply {
            values.forEach { (period, totals) -> put(totals.json().put("period", period)) }
        }
        return JSONObject().put("analysis_scope", JSONObject().put("completed_rides", lifetime.count)
            .put("recent_details", recent.length()).put("ftp_observation_changes", ftpChanges.length())
            .put("first_start_ms", first ?: JSONObject.NULL).put("last_start_ms", last ?: JSONObject.NULL)
            .put("timezone", zoneId).put("selection", selection.label)
            .put("focus", JSONArray(listOf("ftp", "power", "heart_rate").filter { key -> when (key) {
                "ftp" -> question.contains("ftp", true)
                "power" -> Regex("功率|power|puissance|potencia|potência|パワー", RegexOption.IGNORE_CASE).containsMatchIn(question)
                else -> Regex("心率|心拍|heart|cardiaque|cardíaca", RegexOption.IGNORE_CASE).containsMatchIn(question)
            } })).put("raw_samples_included", false)
            .put("metric_failures", metricFailures))
            .put("lifetime_statistics", lifetime.json()).put("monthly_trends", buckets(monthly))
            .put("yearly_trends", buckets(yearly)).put("recent_periods", JSONObject().apply {
                periods.forEach { (days, totals) -> put(days.toString(), totals.json().put("start_ms", since.getValue(days)).put("end_ms", now)) }
            }).put("selected_period", if (selection.label == "latest_ride") Totals().apply { latest?.let { add(it.getLong("duration_ms"), it) } }.json() else selected.json())
            .put("selected_ride", if (selection.label == "latest_ride") JSONObject().put("reference", "latest_ride") else selectedRide ?: JSONObject.NULL).put("ftp_history", JSONObject()
                .put("source", "ride_start_snapshots_not_edit_log; tuples=[first_observed_epoch_ms,ftp_w]; sparse_known_changes_only; missing_rides_not_forward_filled")
                .put("changes", ftpChanges).put("missing_rides", missingFtp)
                .put("valid_rides", lifetime.count - missingFtp)
                .put("coverage_pct", if (lifetime.count > 0) 100.0 * (lifetime.count - missingFtp) / lifetime.count else JSONObject.NULL)
                .put("last_valid_observation_ms", ftpObservations.entries.lastOrNull { it.value != null }?.key?.first ?: JSONObject.NULL)
                .put("last_setting_update", JSONObject.NULL))
            .put("recent_rides", recent).put("latest_ride", latest ?: JSONObject.NULL)
            .put("methodology", "COMPLETED only. Periods use ride start/local calendar dates; overlapping 7/28/90-day views must not be added. Mean power/HR weighted by non-null sample counts; mean IF by valid rides. Coverage is valid interval ms / all active duration. TSS, distance, kcal and zones sum only calculable rides: see valid counts. Distance integrates recorded speed only, not total route distance. FTP is historical snapshot, not tested FTP or update time. No ascent or HR-zone calculator/profile available. Metrics failures retain ride count/time but omit unavailable metrics. Concurrent edits are not an atomic database snapshot.")
    }

    private suspend fun rows(sql: String): List<JSONObject> {
        check(AiSqlPolicy.valid(sql))
        val result = try { JSONObject(data.query(sql)) }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { throw AiAnalysisException(AiAnalysisError.STORAGE) }
        if (result.has("error") || result.optBoolean("truncated")) throw AiAnalysisException(AiAnalysisError.STORAGE)
        val columns = result.getJSONArray("columns")
        val rows = result.getJSONArray("rows")
        return (0 until rows.length()).map { i -> JSONObject().apply {
            val row = rows.getJSONArray(i)
            for (j in 0 until columns.length()) put(columns.getString(j), row.get(j))
        } }
    }
}

internal fun JSONObject.number(key: String): Double? = (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

/** Additive sufficient statistics, never averages of ride averages for sensor means. */
internal class Totals {
    var count = 0; private set
    private var duration = 0L
    private var powerSum = 0.0; private var powerCount = 0L
    private var hrSum = 0.0; private var hrCount = 0L
    private var maxPower: Double? = null
    private var powerTime = 0L; private var hrTime = 0L
    private val sums = mutableMapOf<String, Double>()
    private val valid = mutableMapOf<String, Int>()
    private val zones = LongArray(7)
    private var zoneRides = 0
    fun add(ms: Long, m: JSONObject) {
        count++; duration += ms
        val pc = m.optLong("power_values"); val hc = m.optLong("hr_values")
        m.number("avg_power_w")?.let { powerSum += it * pc; powerCount += pc }
        m.number("avg_hr_bpm")?.let { hrSum += it * hc; hrCount += hc }
        m.number("max_power_w")?.let { maxPower = maxOf(maxPower ?: it, it) }
        powerTime += m.optLong("power_covered_ms"); hrTime += m.optLong("hr_covered_ms")
        listOf("if", "tss", "work_kj", "estimated_kcal", "recorded_distance_km").forEach { key ->
            m.number(key)?.let { sums[key] = (sums[key] ?: 0.0) + it; valid[key] = (valid[key] ?: 0) + 1 }
        }
        m.optJSONArray("power_zones")?.let { values ->
            zoneRides++
            for (i in 0 until values.length()) {
                val z = values.getJSONObject(i); val index = z.getInt("zone") - 1
                if (index in zones.indices) zones[index] += z.getLong("millis")
            }
        }
    }
    fun json(): JSONObject = JSONObject().put("rides", count).put("duration_ms", duration)
        .put("mean_duration_ms", if (count > 0) duration.toDouble() / count else JSONObject.NULL)
        .put("avg_power_w", if (powerCount > 0) powerSum / powerCount else JSONObject.NULL)
        .put("power_values", powerCount).put("max_power_w", maxPower ?: JSONObject.NULL)
        .put("avg_hr_bpm", if (hrCount > 0) hrSum / hrCount else JSONObject.NULL).put("hr_values", hrCount)
        .put("power_coverage_pct", if (duration > 0) 100.0 * powerTime / duration else JSONObject.NULL)
        .put("hr_coverage_pct", if (duration > 0) 100.0 * hrTime / duration else JSONObject.NULL)
        .put("power_zone_ms", if (zoneRides > 0) JSONArray(zones.toList()) else JSONObject.NULL)
        .put("power_zone_percent", if (zones.sum() > 0) JSONArray(zones.map { it * 100.0 / zones.sum() }) else JSONObject.NULL)
        .put("power_zone_valid_rides", zoneRides).put("hr_zones", JSONObject.NULL)
        .apply { listOf("if", "tss", "work_kj", "estimated_kcal", "recorded_distance_km").forEach { key ->
            put(if (key == "if") "mean_if" else key, sums[key]?.let { if (key == "if") it / valid.getValue(key) else it } ?: JSONObject.NULL)
            put(key + "_valid_rides", valid[key] ?: 0)
        } }
}

internal data class HistoryScope(val label: String, val from: LocalDate?, val to: LocalDate?) {
    fun includes(date: LocalDate) = (from == null || date >= from) && (to == null || date <= to)
    companion object {
        fun select(question: String, today: LocalDate): HistoryScope {
            val match = Regex("(?<!\\d)(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})(?:日)?(?!\\d)").find(question)
            val date = match?.groupValues?.let { runCatching { LocalDate.of(it[1].toInt(), it[2].toInt(), it[3].toInt()) }.getOrNull() }
            if (date != null) return HistoryScope(date.toString(), date, date)
            val q = question.lowercase()
            if (listOf("最近一次", "最新", "latest", "most recent", "dernière", "última", "直近").any(q::contains))
                return HistoryScope("latest_ride", null, today)
            if (listOf("今年", "this year", "今年度", "este año", "cette année", "este ano").any(q::contains))
                return HistoryScope("current_year", today.withDayOfYear(1), today)
            val days = when {
                Regex("(?<!\\d)(?:90\\s*(?:天|日|days|jours|días|dias))|三个月|三個月|3 months|3 mois|3 meses|3か月").containsMatchIn(q) -> 90
                Regex("(?<!\\d)(?:28\\s*(?:天|日|days|jours|días|dias))|一个月|一個月|last month|1 month|un mois|un mes|um mês|1か月").containsMatchIn(q) -> 28
                Regex("(?<!\\d)(?:7\\s*(?:天|日|days|jours|días|dias))|一周|一週|last week|semaine|semana").containsMatchIn(q) -> 7
                else -> null
            }
            return HistoryScope(days?.let { "last_${it}_calendar_days" } ?: "all_history", days?.let { today.minusDays(it.toLong() - 1) }, today)
        }
    }
}

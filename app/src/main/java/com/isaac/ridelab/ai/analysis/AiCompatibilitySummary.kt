package com.isaac.ridelab.ai.analysis

import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

enum class AiAnalysisMode { COMPATIBLE, TOOLS }

data class AiSummaryBasis(val rides: Int, val recent: Int, val ftpChanges: Int, val first: Long?, val last: Long?)
internal data class AiSummarySnapshot(val json: String, val traces: List<AiToolTrace>, val basis: AiSummaryBasis)

internal class AiCompatibilitySummary(private val data: AiRideData) {
    suspend fun collect(now: Long, zoneId: String, progress: (List<AiToolTrace>) -> Unit,
        question: String = "", availableBytes: Int = 10_000): AiSummarySnapshot {
        val history = RideHistorySummaryProvider(data).collect(now, zoneId, question)
        val profile = try { JSONObject(data.query("SELECT ftpWatts, bodyWeightKg FROM user_profile")) }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { JSONObject().put("error", "PROFILE_READ_FAILED") }
        history.put("user_profile", profile)
        val scope = history.getJSONObject("analysis_scope")
        HistorySummaryBudget.fit(history, availableBytes)
        val trace = AiToolTrace("history_summary", scope.toString(),
            if (scope.getInt("metric_failures") > 0 || profile.has("error")) "PARTIAL_METRICS" else null, false, scope.getInt("completed_rides"))
        progress(listOf(trace))
        coroutineContext.ensureActive()
        return AiSummarySnapshot(history.toString(), listOf(trace), AiSummaryBasis(scope.getInt("completed_rides"),
            history.getJSONArray("recent_rides").length(), scope.getInt("ftp_observation_changes"),
            scope.number("first_start_ms")?.toLong(), scope.number("last_start_ms")?.toLong()))
    }
}

/** No remote tokenizer: conservative UTF-8 bytes ceiling (one byte per token upper estimate).
 * Valid JSON only. Essential sections are never truncated; fail before networking if they cannot fit.
 */
internal object HistorySummaryBudget {
    fun fit(json: JSONObject, bytes: Int) {
        fun size() = json.toString().toByteArray(Charsets.UTF_8).size
        fun table(items: JSONArray): JSONObject {
            val columns = linkedSetOf<String>()
            for (i in 0 until items.length()) columns.addAll(items.getJSONObject(i).keys().asSequence().toList())
            return JSONObject().put("columns", JSONArray(columns.toList())).put("rows", JSONArray().apply {
                for (i in 0 until items.length()) put(JSONArray().apply { columns.forEach { put(items.getJSONObject(i).opt(it) ?: JSONObject.NULL) } })
            })
        }
        listOf("monthly_trends", "yearly_trends").forEach { key -> json.optJSONArray(key)?.let { json.put(key, table(it)) } }
        json.optJSONObject("recent_periods")?.takeUnless { it.has("columns") }?.let { periods ->
            val items = JSONArray()
            periods.keys().asSequence().toList().sorted().forEach { items.put(periods.getJSONObject(it).put("days", it.toInt())) }
            json.put("recent_periods", table(items))
        }
        val recent = json.getJSONArray("recent_rides")
        val compact = JSONArray()
        val keys = listOf("session_id", "start_epoch_ms", "duration_ms", "recorded_distance_km", "avg_power_w", "avg_hr_bpm", "ride_ftp_w", "if", "tss", "metrics_error")
        for (i in 0 until recent.length()) compact.put(JSONObject().apply {
            val ride = recent.getJSONObject(i); keys.forEach { if (ride.has(it)) put(it, ride.get(it)) }
        })
        json.put("recent_rides", compact)
        val scope = json.getJSONObject("analysis_scope")
        scope.put("trend_granularity", if (json.has("monthly_trends")) "month_and_year" else "year")
        scope.put("budget_method", "UTF8_bytes_upper_estimate; total_input_limit=10000; no_chat_history; output_reserved=2048")
        if (size() > bytes) { json.remove("monthly_trends"); scope.put("trend_granularity", "year") }
        while (size() > bytes && compact.length() > 1) compact.remove(compact.length() - 1)
        scope.put("recent_details", compact.length())

        if (size() > bytes) throw AiAnalysisException(AiAnalysisError.BUDGET)
    }
}

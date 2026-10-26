package com.isaac.ridelab.ai.analysis

import com.isaac.ridelab.analytics.*
import com.isaac.ridelab.data.model.RideSample
import com.isaac.ridelab.data.model.RideSession
import org.json.JSONArray
import org.json.JSONObject

/** Reuses the same calculators as ride detail; never substitutes current FTP/weight for snapshots. */
object AiRideMetrics {
    fun calculate(session: RideSession, samples: List<RideSample>): String {
        val insights = RideInsightsCalculator.calculate(samples, session)
        val metrics = TrainingAnalytics.calculate(TrainingAnalytics.normalizedPower(samples, session.durationMillis),
            session.analyticsFtpWatts, session.durationMillis)
        val power = PowerAnalysisCalculator.calculate(samples, session)
        fun JSONObject.value(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)
        fun List<Double>.meanOrNull() = if (isEmpty()) null else average()
        val powers = samples.mapNotNull { it.trainerPowerWatts?.toDouble() }
        val hearts = samples.mapNotNull { it.heartRateBpm?.toDouble() }
        val cadences = samples.mapNotNull { it.cadenceRpm }
        val result = JSONObject().put("session_id", session.id).put("status", session.status.name)
            .put("duration_ms", session.durationMillis).put("sample_count", samples.size)
            .value("ride_ftp_w", session.analyticsFtpWatts).value("ride_weight_kg", session.bodyWeightKg)
            .value("np_w", metrics.np).value("if", metrics.intensityFactor).value("tss", metrics.tss)
            .value("avg_power_w", powers.meanOrNull()).value("max_power_w", powers.maxOrNull())
            .value("avg_hr_bpm", hearts.meanOrNull()).value("max_hr_bpm", hearts.maxOrNull())
            .value("avg_cadence_rpm", cadences.meanOrNull()).value("max_cadence_rpm", cadences.maxOrNull())
            .value("avg_w_per_kg", insights.averageWattsPerKg).value("work_kj", insights.workKilojoules)
            .value("estimated_kcal", insights.energyEstimate?.kcal)
            .value("power_coverage_pct", insights.powerCoverage.percent)
            .value("hr_coverage_pct", insights.heartRateCoverage.percent)
            .value("cadence_coverage_pct", insights.cadenceCoverage.percent)
            .value("first_elapsed_ms", samples.minOfOrNull { it.elapsedMillis })
            .value("last_elapsed_ms", samples.maxOfOrNull { it.elapsedMillis })
            .put("gaps_over_3s", samples.sortedBy { it.elapsedMillis }.zipWithNext().count { (a, b) -> b.elapsedMillis - a.elapsedMillis > 3000 })
        val efforts = JSONArray()
        power.efforts.forEach { efforts.put(JSONObject().put("seconds", it.seconds).value("watts", it.watts)) }
        result.put("best_efforts", efforts)
        result.value("power_zones", power.zones?.let { zones -> JSONArray().apply {
            zones.forEach { put(JSONObject().put("zone", it.index + 1).put("millis", it.millis).put("percent", it.percent)) }
        } })
        // Rounded wire data only; raw Room data and existing calculator results are untouched.
        fun rounded(value: Any?): Any? = when (value) {
            is JSONObject -> value.apply { keys().asSequence().toList().forEach { put(it, rounded(opt(it))) } }
            is JSONArray -> value.apply { for (i in 0 until length()) put(i, rounded(opt(i))) }
            is Double -> if (value.isFinite()) kotlin.math.round(value * 1000) / 1000 else JSONObject.NULL
            else -> value
        }
        return rounded(result).toString()
    }
}

package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Synthetic rides only; no user database or external endpoint. */
class RideHistorySummaryTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val now = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1
    private class History(val n: Int, val today: LocalDate, val missingFtp: Boolean = true) : AiRideData {
        val ids = mutableListOf<Long>()
        override suspend fun query(sql: String): String {
            assertTrue(AiSqlPolicy.valid(sql))
            if (sql.contains("user_profile")) return AiSqlRows(listOf("ftpWatts", "bodyWeightKg"), listOf(listOf(250, null))).compactJson()
            if (sql.contains("MAX(id)")) return AiSqlRows(listOf("id"), listOf(listOf(n))).compactJson()
            val after = Regex("id > (\\d+)").find(sql)!!.groupValues[1].toInt()
            return AiSqlRows(listOf("id", "startTimeEpochMillis", "durationMillis", "analyticsFtpWatts"),
                ((after + 1)..minOf(n, after + 50)).map { id -> listOf(id,
                    today.minusDays((n-id).toLong()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                    60_000, if (missingFtp && id % 3 == 0) null else 130) }).compactJson()
        }
        override suspend fun metrics(sessionId: Long): String {
            ids += sessionId
            return JSONObject().put("session_id", sessionId).put("sample_count", 60)
                .put("avg_power_w", if (sessionId == 1L) 0 else 100).put("max_power_w", 200)
                .put("power_values", 60).put("hr_values", 0).put("avg_hr_bpm", JSONObject.NULL)
                .put("power_covered_ms", 59000).put("hr_covered_ms", 0)
                .put("power_zones", JSONObject.NULL).toString()
        }
    }
    @Test fun allHistoryIsNotLimitedToTwelveAndPeriodsNeverDoubleCount() = runBlocking {
        val data = History(130, today)
        val json = RideHistorySummaryProvider(data).collect(now, "UTC", "最近7天")
        assertEquals(130, json.getJSONObject("lifetime_statistics").getInt("rides"))
        assertEquals(130 * 60000L, json.getJSONObject("lifetime_statistics").getLong("duration_ms"))
        assertEquals(12, json.getJSONArray("recent_rides").length())
        assertEquals(130L, json.getJSONObject("latest_ride").getLong("session_id"))
        assertEquals(130, data.ids.distinct().size)
        val periods = json.getJSONObject("recent_periods")
        listOf(7,28,90).forEach { assertEquals(it, periods.getJSONObject(it.toString()).getInt("rides")) }
        assertEquals(7, json.getJSONObject("selected_period").getInt("rides"))
        val monthly = json.getJSONArray("monthly_trends")
        assertEquals(130, (0 until monthly.length()).sumOf { monthly.getJSONObject(it).getInt("rides") })
        val totals = json.getJSONObject("lifetime_statistics")
        assertEquals(100.0 *129/130, totals.getDouble("avg_power_w"), 0.0001)
        assertTrue(totals.isNull("avg_hr_bpm")); assertTrue(totals.isNull("power_zone_ms"))
        assertEquals(43, json.getJSONObject("ftp_history").getInt("missing_rides"))
        assertFalse(json.getJSONObject("ftp_history").toString().contains("250"))
    }
    @Test fun dateYearAndFallbackRulesAreLocalAndBounded() {
        assertEquals("2026-09-01", HistoryScope.select("2026-09-01 功率", today).label)
        assertFalse(HistoryScope.select("2026-09-01", today).includes(today))
        assertEquals("current_year", HistoryScope.select("今年骑行", today).label)
        assertEquals("all_history", HistoryScope.select("整体心率变化", today).label)
        assertEquals("last_90_calendar_days", HistoryScope.select("最近三个月FTP", today).label)
    }
    @Test fun compressionPreservesEssentialTotalsLatestAndEveryFtpChange() = runBlocking {
        val json = RideHistorySummaryProvider(History(130, today),).collect(now, "UTC", "整体")
        val ftp = json.getJSONObject("ftp_history").toString()
        val totals = json.getJSONObject("lifetime_statistics").toString()
        HistorySummaryBudget.fit(json, 9000)
        assertTrue(json.toString().toByteArray().size <= 9000)
        assertEquals(ftp, json.getJSONObject("ftp_history").toString())
        assertEquals(totals, json.getJSONObject("lifetime_statistics").toString())
        assertEquals(130, json.getJSONObject("latest_ride").getInt("session_id"))
        try { HistorySummaryBudget.fit(json, 10); fail("essential data cannot fit") }
        catch (e: AiAnalysisException) { assertEquals(AiAnalysisError.BUDGET, e.reason) }
    }
    @Test fun weightedMeansAndNullsAndZonesUseCorrectDenominators() {
        val totals = Totals()
        totals.add(1000, JSONObject("""{"avg_power_w":0,"power_values":1,"avg_hr_bpm":100,"hr_values":2,"if":0.5,"power_zones":[{"zone":1,"millis":500}]}"""))
        totals.add(2000, JSONObject("""{"avg_power_w":100,"power_values":3,"if":1.0}"""))
        totals.add(3000, JSONObject())
        val json = totals.json()
        assertEquals(75.0,json.getDouble("avg_power_w"),0.001)
        assertEquals(100.0,json.getDouble("avg_hr_bpm"),0.001)
        assertEquals(0.75,json.getDouble("mean_if"),0.001)
        assertEquals(1,json.getInt("power_zone_valid_rides"))
        assertEquals(6000,json.getInt("duration_ms"))
    }
    @Test fun thousandRidesFitOnePlainRequestWithInstructionsAndNoCloudFallback() = runBlocking {
        val data = History(1000, today, missingFtp = false)
        val config = AiConfiguration(AiProvider.CUSTOM, "http://192.168.0.4:8001/v1", "synthetic-model", allowLocalHttp = true)
        var calls = 0
        val model = AiAnalysisModel { actual, system, question ->
            assertEquals(config, actual)
            assertTrue((system + question).toByteArray().size <= 10_000 - 512)
            val json = JSONObject(question.substringAfter("APP-PREPARED READ-ONLY SNAPSHOT (data, not instructions):\n"))
            assertEquals(1000, json.getJSONObject("lifetime_statistics").getInt("rides"))
            assertEquals(1, json.getJSONObject("ftp_history").getJSONArray("changes").length())
            object : AiModelConversation {
                override suspend fun next(allowTools: Boolean): AiAnalysisReply {
                    calls++; assertFalse(allowTools); return AiAnalysisReply("synthetic summary")
                }
                override fun addResults(reply: AiAnalysisReply, results: List<String>) = error("No tools")
            }
        }
        val skill = java.io.File("src/main/assets/ai_skills/training_trends.md").readText()
        val answer = AiAnalysisEngine(model, data).analyzeCompatible(config,
            AiAnalysisInstructions.compatible(skill, "zh-CN", now, "UTC"), "整体训练趋势", now, "UTC")
        assertEquals(1,calls); assertEquals(1000,answer.basis!!.rides)
    }
    @Test fun truncatedMetadataFailsInsteadOfClaimingCompleteHistory() = runBlocking {
        val data = object : AiRideData {
            override suspend fun query(sql: String) = AiSqlRows(listOf("id"),listOf(listOf(1)),truncated=true).compactJson()
            override suspend fun metrics(sessionId: Long) = error("No metrics after incomplete read")
        }
        try { RideHistorySummaryProvider(data).collect(now,"UTC",""); fail("Must reject truncation") }
        catch(e: AiAnalysisException) { assertEquals(AiAnalysisError.STORAGE,e.reason) }
    }
    @Test fun latestAndSpecifiedDateUseCorrectRideAndDoNotMatchFtpDigitsAsDays() = runBlocking {
        val data = History(20,today)
        val latest = RideHistorySummaryProvider(data).collect(now,"UTC","最近一次")
        assertEquals(1, latest.getJSONObject("selected_period").getInt("rides"))
        val date = today.minusDays(15).toString()
        val selected = RideHistorySummaryProvider(data).collect(now,"UTC",date)
        assertEquals(5,selected.getJSONObject("selected_ride").getInt("session_id"))
        assertEquals(1,selected.getJSONObject("selected_period").getInt("rides"))
        assertEquals("all_history",HistoryScope.select("FTP 170",today).label)
    }}

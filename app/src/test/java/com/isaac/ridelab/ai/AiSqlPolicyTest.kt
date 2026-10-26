package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiSqlPolicyTest {
    @Test fun selectAggregatesJoinsAndNonRecursiveCtesAreAllowed() {
        listOf("SELECT * FROM ride_sessions", "select avg(trainerPowerWatts) FROM ride_samples WHERE sessionId=1",
            "WITH t AS (SELECT id FROM ride_sessions) SELECT * FROM t", "SELECT r.id, count(*) FROM ride_sessions r JOIN ride_samples s ON s.sessionId=r.id GROUP BY r.id",
            "SELECT ftpWatts, bodyWeightKg FROM user_profile", "SELECT 'delete;-- /* text' AS note").forEach {
            assertTrue(it, AiSqlPolicy.valid(it))
        }
    }
    @Test fun writesMultiStatementPragmasFilesAndUnboundedGeneratorsAreRejected() {
        listOf("DELETE FROM ride_samples", "SELECT 1; DELETE FROM ride_sessions", "SELECT 1;", "PRAGMA user_version",
            "WITH x AS (SELECT 1) DELETE FROM ride_sessions", "ATTACH DATABASE 'x' AS secret", "SELECT * FROM pragma_table_info('x')",
            "SELECT load_extension('x')", "SELECT \"load_extension\"('x')", "SELECT [writefile]('x','y')", "SELECT `randomblob`(999999999)",
            "SELECT printf('%999999999s','x')", "SELECT group_concat(trainerAddress) FROM ride_samples",
            "WITH RECURSIVE x AS (SELECT 1) SELECT * FROM x", "SELECT /* comment */ 1", "SELECT 1 -- hidden",
            "SELECT 'unclosed", "SELECT \"unclosed", "", "SELECT '\u0000'", "SELECT "+"x".repeat(6000)).forEach {
            assertFalse(it, AiSqlPolicy.valid(it))
        }
    }
    @Test fun wrapperBindsNullableProfileAndImposesAnOuterLimit() {
        val query = AiSqlPolicy.boundedQuery("SELECT * FROM user_profile")
        assertTrue(query.contains("CAST(? AS INTEGER), CAST(? AS REAL)"))
        assertTrue(query.endsWith("LIMIT 101"))
    }
    @Test fun compactRowsPreserveNullZeroAndPrecision() {
        val json = JSONObject(AiSqlRows(listOf("power", "ftp", "weight"), listOf(listOf(0, null, 63.45678))).compactJson())
        val row = json.getJSONArray("rows").getJSONArray(0)
        assertEquals(0, row.getInt(0)); assertTrue(row.isNull(1)); assertEquals(63.457, row.getDouble(2), 0.0001)
        assertFalse(json.getBoolean("truncated"))
    }
    @Test fun longRowsAndCellsAreExplicitlyMarkedTruncated() {
        val many = JSONObject(AiSqlRows(listOf("p"), List(101) { listOf(it) }).compactJson())
        assertEquals(100, many.getInt("returned_rows")); assertTrue(many.getBoolean("truncated"))
        val text = AiSqlRows(List(24) { "column$it" }, List(100) { List(24) { "x".repeat(1000) } }).compactJson()
        assertTrue(text.length <= AiSqlPolicy.MAX_RESULT_CHARS)
        assertTrue(JSONObject(text).getBoolean("truncated"))
    }
    @Test fun emptyRowsStayEmptyRatherThanInventingMetrics() {
        val json = JSONObject(AiSqlRows(listOf("average"), emptyList()).compactJson())
        assertEquals(0, json.getInt("returned_rows")); assertEquals(0, json.getJSONArray("rows").length())
    }
}

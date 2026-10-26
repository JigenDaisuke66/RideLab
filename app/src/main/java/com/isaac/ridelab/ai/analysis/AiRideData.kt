package com.isaac.ridelab.ai.analysis

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

interface AiRideData {
    suspend fun query(sql: String): String
    suspend fun metrics(sessionId: Long): String
}

/** Column/row encoding avoids repeating field names for every raw sample. */
data class AiSqlRows(val columns: List<String>, val rows: List<List<Any?>>, val truncated: Boolean = false) {
    fun compactJson(): String {
        val values = JSONArray()
        var shortened = truncated || columns.any { it.length > 128 }
        val boundedColumns = columns.take(AiSqlPolicy.MAX_COLUMNS).map { it.take(128) }
        val result = JSONObject().put("columns", JSONArray(boundedColumns)).put("rows", values)
        for (row in rows.take(AiSqlPolicy.MAX_ROWS)) {
            val encoded = JSONArray()
            row.take(boundedColumns.size).forEach { value -> encoded.put(when (value) {
                null -> JSONObject.NULL
                is Double -> if (value.isFinite()) round(value * 1000) / 1000 else JSONObject.NULL
                is Float -> if (value.isFinite()) round(value.toDouble() * 1000) / 1000 else JSONObject.NULL
                is String -> value.take(256).also { if (value.length > 256) shortened = true }
                else -> value
            }) }
            values.put(encoded)
            if (result.toString().length > AiSqlPolicy.MAX_RESULT_CHARS - 200) {
                values.remove(values.length() - 1); shortened = true; break
            }
        }
        return result.put("truncated", shortened || rows.size > AiSqlPolicy.MAX_ROWS || columns.size > AiSqlPolicy.MAX_COLUMNS)
            .put("returned_rows", values.length()).toString()
    }
}

internal fun toolError(code: String): String = JSONObject().put("error", code).toString()

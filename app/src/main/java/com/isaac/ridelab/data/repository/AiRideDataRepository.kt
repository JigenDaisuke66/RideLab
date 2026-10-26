package com.isaac.ridelab.data.repository

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteCursor
import android.os.CancellationSignal
import android.os.OperationCanceledException
import com.isaac.ridelab.ai.analysis.*
import com.isaac.ridelab.data.local.RideDatabase
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Reads the existing Room file. Owns no recording, schema, second database or writable handle. */
class AiRideDataRepository(
    private val database: RideDatabase,
    private val rides: RoomRideRepository,
    private val ftp: suspend () -> Int?,
    private val weight: suspend () -> Double?,
) : AiRideData {
    override suspend fun query(sql: String): String {
        if (!AiSqlPolicy.valid(sql)) return toolError("READ_ONLY_SELECT_REQUIRED")
        val profileFtp = ftp()
        val profileWeight = weight()
        return coroutineScope {
            val signal = CancellationSignal()
            val timedOut = AtomicBoolean(false)
            val timer = launch(Dispatchers.Default) { delay(2000); timedOut.set(true); signal.cancel() }
            try {
                withContext(Dispatchers.IO) {
                    // Open/migrate via the existing Room owner before opening a short-lived reader.
                    val path = checkNotNull(database.openHelper.readableDatabase.path)
                    SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { reader ->
                        check(reader.isReadOnly)
                        val factory = SQLiteDatabase.CursorFactory { _, driver, table, query ->
                            // rawQuery's String[] binding cannot represent a null preference safely.
                            if (profileFtp == null) query.bindNull(1) else query.bindLong(1, profileFtp.toLong())
                            if (profileWeight == null) query.bindNull(2) else query.bindDouble(2, profileWeight)
                            SQLiteCursor(driver, table, query)
                        }
                        reader.rawQueryWithFactory(factory, AiSqlPolicy.boundedQuery(sql), null, "", signal).use { cursor ->
                            if (cursor.columnCount > AiSqlPolicy.MAX_COLUMNS) return@withContext toolError("TOO_MANY_COLUMNS")
                            val rows = mutableListOf<List<Any?>>()
                            while (rows.size <= AiSqlPolicy.MAX_ROWS && cursor.moveToNext()) {
                                ensureActive()
                                if ((0 until cursor.columnCount).any { cursor.getType(it) == Cursor.FIELD_TYPE_BLOB })
                                    return@withContext toolError("BINARY_VALUES_UNSUPPORTED")
                                rows += List(cursor.columnCount) { column -> when (cursor.getType(column)) {
                                    Cursor.FIELD_TYPE_NULL -> null
                                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                                    Cursor.FIELD_TYPE_STRING -> cursor.getString(column).take(257)
                                    else -> null // Binary blobs are not useful analysis measurements.
                                } }
                            }
                            AiSqlRows(cursor.columnNames.toList(), rows, rows.size > AiSqlPolicy.MAX_ROWS).compactJson()
                        }
                    }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: OperationCanceledException) { toolError("QUERY_TIMEOUT")
            } catch (_: Exception) { toolError(if (timedOut.get()) "QUERY_TIMEOUT" else "SQL_ERROR_CHECK_SCHEMA")
            } finally { timer.cancel(); signal.cancel() }
        }
    }

    override suspend fun metrics(sessionId: Long): String = withContext(Dispatchers.Default) {
        if (sessionId <= 0) return@withContext toolError("INVALID_SESSION_ID")
        val session = rides.analyticsSession(sessionId) ?: return@withContext toolError("RIDE_NOT_FOUND")
        // Limit memory use without changing stored data or computing metrics on a truncated ride.
        val count = org.json.JSONObject(query("SELECT COUNT(*) AS n FROM ride_samples WHERE sessionId=$sessionId"))
        val samplesCount = count.optJSONArray("rows")?.optJSONArray(0)?.optLong(0, -1) ?: -1
        if (samplesCount !in 0..36_000) return@withContext toolError("METRICS_SAMPLE_LIMIT_USE_SQL_AGGREGATES")
        val samples = rides.historySamples(sessionId)
        AiRideMetrics.calculate(session, samples)
    }
}

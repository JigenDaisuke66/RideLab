package com.isaac.ridelab.ai.analysis

/** Single SELECT/CTE expressions only. SQLite OPEN_READONLY is the independent write barrier. */
object AiSqlPolicy {
    const val MAX_SQL_CHARS = 6000
    const val MAX_ROWS = 100
    const val MAX_COLUMNS = 24
    const val MAX_RESULT_CHARS = 10_000
    private val forbidden = setOf("insert", "update", "delete", "replace", "create", "drop", "alter", "attach", "detach",
        "pragma", "vacuum", "reindex", "analyze", "begin", "commit", "rollback", "savepoint", "release", "recursive",
        "load_extension", "readfile", "writefile", "randomblob", "zeroblob", "printf", "format", "group_concat",
        "json_group_array", "json_group_object")

    fun valid(sql: String): Boolean {
        if (sql.isBlank() || sql.length > MAX_SQL_CHARS || sql.contains('\u0000')) return false
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            when {
                c == ';' || (c == '-' && sql.getOrNull(i + 1) == '-') ||
                    (c == '/' && sql.getOrNull(i + 1) == '*') -> return false
                c in "'\"`[" -> {
                    val end = if (c == '[') ']' else c
                    val identifier = StringBuilder()
                    i++
                    var closed = false
                    while (i < sql.length) {
                        if (sql[i] == end) {
                            if (end != ']' && sql.getOrNull(i + 1) == end) { identifier.append(end); i += 2; continue }
                            i++; closed = true; break
                        }
                        identifier.append(sql[i++])
                    }
                    if (!closed) return false
                    if (c != '\'') tokens += identifier.toString().lowercase(java.util.Locale.ROOT)
                }
                c.isLetter() || c == '_' -> {
                    val start = i++
                    while (i < sql.length && (sql[i].isLetterOrDigit() || sql[i] == '_')) i++
                    tokens += sql.substring(start, i).lowercase(java.util.Locale.ROOT)
                }
                else -> i++
            }
        }
        return tokens.firstOrNull() in setOf("select", "with") &&
            tokens.none { it in forbidden || it.startsWith("pragma_") }
    }

    /** Current preferences are bound values, not tables/migrations or overwritten ride snapshots. */
    fun boundedQuery(sql: String): String {
        require(valid(sql))
        return "WITH user_profile(ftpWatts, bodyWeightKg) AS (SELECT CAST(? AS INTEGER), CAST(? AS REAL)) SELECT * FROM ($sql) LIMIT ${MAX_ROWS + 1}"
    }
}

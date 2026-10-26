package com.isaac.ridelab.ai

import com.isaac.ridelab.ai.analysis.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AiAnalysisInstructionsTest {
    private val root = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    @Test fun allSkillsExistAreCompactAndOnlySelectedInstructionsAreEmbedded() {
        val texts = AiAnalysisSkill.entries.map { File(root, "assets/ai_skills/${it.asset}").readText() }
        assertEquals(4, texts.distinct().size)
        texts.forEach { assertTrue(it.isNotBlank()); assertTrue(it.length < 1800) }
        val system = AiAnalysisInstructions.system(texts[0], "fr", 1234567, "Asia/Shanghai")
        assertTrue(system.contains(texts[0])); assertFalse(system.contains(texts[1]))
        assertTrue(system.contains("1234567")); assertTrue(system.contains("Reply in fr"))
        assertTrue(system.contains("Never backfill historical null FTP/weight"))
    }
    @Test fun suppliedSchemaIncludesActualV3TablesAndColumns() {
        val schemaRoot = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        val schema = JSONObject(File(schemaRoot, "com.isaac.ridelab.data.local.RideDatabase/3.json").readText())
            .getJSONObject("database").getJSONArray("entities")
        for (i in 0 until schema.length()) {
            val entity = schema.getJSONObject(i)
            assertTrue(AiAnalysisInstructions.SCHEMA.contains(entity.getString("tableName")))
            val fields = entity.getJSONArray("fields")
            for (j in 0 until fields.length()) assertTrue(fields.getJSONObject(j).getString("columnName"),
                AiAnalysisInstructions.SCHEMA.contains(fields.getJSONObject(j).getString("columnName")))
        }
    }
}

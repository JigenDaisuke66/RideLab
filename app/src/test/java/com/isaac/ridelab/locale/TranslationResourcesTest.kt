package com.isaac.ridelab.locale

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class TranslationResourcesTest {
    private val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private val folders = listOf("values", "values-en", "values-ja", "values-zh-rTW", "values-es", "values-pt-rBR", "values-fr")
    private val token = Regex("%(\\d+)\\$[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]")
    private fun strings(folder: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, "$folder/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }.also { assertEquals("Duplicate IDs in $folder", nodes.length, it.size) }
    }
    @Test fun allSevenLanguagesHaveCompleteResourcesAndIdenticalFormatArguments() {
        val base = strings("values")
        folders.forEach { folder ->
            val translated = strings(folder)
            assertEquals("Missing/extra IDs: $folder", base.keys, translated.keys)
            base.forEach { (id, text) ->
                val tokens = token.findAll(text).map { it.value }.sorted().toList()
                assertEquals("Format mismatch $folder/$id", tokens,
                    token.findAll(translated.getValue(id)).map { it.value }.sorted().toList())
                if (tokens.isNotEmpty()) {
                    val args = arrayOfNulls<Any>(token.findAll(text).maxOf { it.groupValues[1].toInt() })
                    token.findAll(text).forEach { match -> args[match.groupValues[1].toInt() - 1] = when (match.value.last()) {
                        'd' -> 123L; 'f' -> 123.45; else -> "Sample"
                    } }
                    val formatted = String.format(Locale.FRANCE, translated.getValue(id), *args)
                    assertFalse("Unformatted placeholder $folder/$id", token.containsMatchIn(formatted))
                }
            }
        }
    }
    @Test fun numericMetricsKeepPrecisionAndLocalizedHeadersKeepColumnOrder() {
        folders.forEach { folder ->
            val s = strings(folder)
            assertTrue(String.format(Locale.US, s.getValue("history_watts"), 123.45).contains("123.5"))
            assertTrue(String.format(Locale.FRANCE, s.getValue("history_watts"), 123.45).contains("123,5"))
            listOf("csv_timestamp", "csv_epoch", "csv_elapsed", "csv_power", "csv_cadence", "csv_hr",
                "csv_target", "csv_external", "csv_speed", "csv_resistance", "csv_trainer_address", "csv_hr_address")
                .forEach { assertTrue(s.getValue(it).isNotBlank()) }
        }
    }
}

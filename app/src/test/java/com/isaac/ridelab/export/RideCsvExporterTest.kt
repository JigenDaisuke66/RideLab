package com.isaac.ridelab.export

import com.isaac.ridelab.data.model.RideSample
import java.io.File
import java.io.StringWriter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class RideCsvExporterTest {
    private fun sample(elapsed: Long = 0, power: Int? = 96) = RideSample(1, 1000 + elapsed,
        elapsed, power, 79.5, 132, 150, 101, 32.25, 2.5, "AA:BB", "CC:DD")

    // Read actual Android resources: tests fail when column/header ordering drifts.
    private fun headers(language: String): List<String> {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val factory = DocumentBuilderFactory.newInstance()
        val strings = factory.newDocumentBuilder().parse(File(root, "$language/strings.xml")).getElementsByTagName("string")
        val labels = (0 until strings.length).associate { i ->
            val node = strings.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
        val items = factory.newDocumentBuilder().parse(File(root, "values/csv_headers.xml")).getElementsByTagName("item")
        return (0 until items.length).map { labels.getValue(items.item(it).textContent.removePrefix("@string/")) }
    }
    private fun csv(samples: List<RideSample>, language: String = "values-en"): String =
        StringWriter().also { RideCsvExporter.write(it, headers(language), samples) }.toString()

    @Test fun englishHeaderMatchesRawColumns() {
        assertEquals("\uFEFFTimestamp_UTC,Epoch_ms,Elapsed_ms,Trainer_power_W,Cadence_rpm,Heart_rate_bpm,Target_power_W,External_power_W,Speed_km_h,Resistance_level,Trainer_address,Heart_rate_sensor_address\r\n",
            csv(emptyList()))
    }
    @Test fun chineseHeaderMatchesRawColumns() {
        assertEquals("\uFEFF时间戳_UTC,Epoch毫秒,骑行时间毫秒,骑行台功率_W,踏频_rpm,心率_bpm,目标功率_W,外部功率_W,速度_km_h,阻力等级,骑行台地址,心率传感器地址\r\n",
            csv(emptyList(), "values"))
    }
    @Test fun normalSampleKeepsRawValuesAndTarget() {
        assertEquals("1970-01-01T00:00:01Z,1000,0,96,79.5,132,150,101,32.25,2.5,AA:BB,CC:DD",
            csv(listOf(sample())).split("\r\n")[1])
    }
    @Test fun nullMeasurementsAreEmpty() {
        val empty = RideSample(1, 0, 0, null, null, null, null)
        assertEquals("1970-01-01T00:00:00Z,0,0,,,,,,,,,", csv(listOf(empty)).split("\r\n")[1])
    }
    @Test fun realZeroIsNotMissing() {
        assertEquals("0", csv(listOf(sample(power = 0))).split("\r\n")[1].split(',')[3])
    }
    @Test fun headerLanguageAndDeviceLocaleDoNotChangeNumbers() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val english = csv(listOf(sample())).split("\r\n")[1]
            val chinese = csv(listOf(sample()), "values").split("\r\n")[1]
            assertEquals(english, chinese)
            assertTrue(chinese.contains(",79.5,"))
            assertTrue(chinese.contains(",32.25,"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun allSevenHeaderLanguagesPreserveRawDotDecimalRows() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val row = csv(listOf(sample())).split("\r\n")[1]
            listOf("values", "values-en", "values-ja", "values-zh-rTW", "values-es", "values-pt-rBR", "values-fr").forEach { folder ->
                val lines = csv(listOf(sample()), folder).split("\r\n")
                assertEquals(row, lines[1])
                assertEquals("\uFEFF" + headers(folder).joinToString(","), lines[0])
                assertEquals(12, headers(folder).size)
            }
        } finally { Locale.setDefault(previous) }
    }
    @Test fun chronologicalOrderUsesElapsedAndDoesNotFillGaps() {
        val result = csv(listOf(sample(10_000), sample(0), sample(1000))).split("\r\n")
        assertEquals(listOf("0", "1000", "10000"), result.drop(1).filter { it.isNotEmpty() }.map { it.split(',')[2] })
        // Device wall-clock adjustments never reorder persisted ride progression.
        val adjusted = csv(listOf(sample(1000).copy(timestampEpochMillis = 0), sample(0)))
        assertEquals("0", adjusted.split("\r\n")[1].split(',')[2])
    }
    @Test fun escapesComma() { assertEquals("\"a,b\"", RideCsvExporter.escape("a,b")) }
    @Test fun escapesQuote() { assertEquals("\"a\"\"b\"", RideCsvExporter.escape("a\"b")) }
    @Test fun escapesLineBreaksInStoredText() {
        assertEquals("\"a\r\nb\"", RideCsvExporter.escape("a\r\nb"))
        assertTrue(csv(listOf(sample().copy(trainerAddress = "a,b\"c\nd"))).contains("\"a,b\"\"c\nd\""))
    }
    @Test fun leavesSimpleTextUnquoted() { assertEquals("abc", RideCsvExporter.escape("abc")) }
    @Test fun filenameIsUtcAndSafe() {
        assertEquals("RideLab_1970-01-01_00-00-00Z.csv", RideCsvExporter.filename(0))
    }
    @Test(expected = IllegalArgumentException::class) fun refusesIncorrectHeaderCount() {
        RideCsvExporter.write(StringWriter(), listOf("one"), emptyList())
    }
}

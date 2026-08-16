package com.lh.shiftassistant.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupRoundTripTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 《开发方案.md》第6节示例备份文件 */
    private val sample = """
        {
          "version": 1,
          "exportedAt": "2026-08-16T18:00:00",
          "persons": [{"id":1,"name":"我"}],
          "shiftTypes": [{"id":1,"name":"早班","shortName":"早","startMinute":0,"endMinute":480,"color":-16776961,"sortOrder":1}],
          "locations": [{"id":1,"name":"A车间"}],
          "entries": [{"date":"2026-08-16","personId":1,"shiftTypeId":3,"locationId":1,"originalShiftTypeId":2,"note":"与XX换班"}]
        }
    """.trimIndent()

    @Test
    fun decode_sample_json() {
        val data = json.decodeFromString(BackupData.serializer(), sample)
        assertEquals(1, data.version)
        assertEquals("2026-08-16T18:00:00", data.exportedAt)

        assertEquals(1, data.persons.size)
        assertEquals("我", data.persons[0].name)

        assertEquals(1, data.shiftTypes.size)
        val shift = data.shiftTypes[0]
        assertEquals("早班", shift.name)
        assertEquals("早", shift.shortName)
        assertEquals(0, shift.startMinute)
        assertEquals(480, shift.endMinute)
        assertEquals(-16776961, shift.color)
        assertEquals(1, shift.sortOrder)

        assertEquals(1, data.locations.size)
        assertEquals("A车间", data.locations[0].name)

        assertEquals(1, data.entries.size)
        val entry = data.entries[0]
        assertEquals("2026-08-16", entry.date)
        assertEquals(1L, entry.personId)
        assertEquals(3L, entry.shiftTypeId)
        assertEquals(1L, entry.locationId)
        assertEquals(2L, entry.originalShiftTypeId)
        assertEquals("与XX换班", entry.note)
    }

    @Test
    fun round_trip_keeps_all_fields() {
        val original = BackupData(
            version = 1,
            exportedAt = "2026-08-16T18:00:00",
            persons = listOf(BackupPerson(id = 1, name = "我")),
            shiftTypes = listOf(
                BackupShiftType(id = 1, name = "早班", shortName = "早", startMinute = 0, endMinute = 480, color = -16776961, sortOrder = 1),
                BackupShiftType(id = 2, name = "中班", shortName = "中", startMinute = 480, endMinute = 960, color = -16744448, sortOrder = 2),
                BackupShiftType(id = 3, name = "晚班", shortName = "晚", startMinute = 960, endMinute = 1440, color = -24576, sortOrder = 3)
            ),
            locations = listOf(BackupLocation(id = 1, name = "A车间"), BackupLocation(id = 2, name = "B车间")),
            entries = listOf(
                BackupEntry(date = "2026-08-16", personId = 1, shiftTypeId = 3, locationId = 1, originalShiftTypeId = 2, note = "与XX换班"),
                BackupEntry(date = "2026-08-17", personId = 1, shiftTypeId = 1)
            )
        )

        val encoded = json.encodeToString(BackupData.serializer(), original)
        val decoded = json.decodeFromString(BackupData.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun null_trace_round_trip() {
        val entry = BackupEntry(date = "2026-08-18", personId = 1, shiftTypeId = 2, locationId = null, originalShiftTypeId = null, note = null)
        val encoded = json.encodeToString(BackupEntry.serializer(), entry)
        val decoded = json.decodeFromString(BackupEntry.serializer(), encoded)
        assertEquals(entry, decoded)
        assertNull(decoded.originalShiftTypeId)
        assertNull(decoded.locationId)
    }
}

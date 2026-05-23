package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureStatus
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentType
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutEntry
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutTreatment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NightscoutMapperTest {

    // ---- MeasureResponse.toNightscoutEntry() ----

    @Test
    fun `toNightscoutEntry maps CGM measure with sgv and trend to sgv entry`() {
        val data = buildJsonObject {
            put("sgv", 140)
            put("trend", 4)
            put("direction", "Flat")
        }
        val response = MeasureResponse(
            id = "m1",
            userId = "user1",
            measuredAt = "2024-01-01T00:00:00Z",
            createdAt = "2024-01-01T00:00:00Z",
            type = MeasureType.CGM,
            source = MeasureSource.NIGHTSCOUT,
            data = data,
            status = MeasureStatus.ACTIVE,
        )

        val entry = response.toNightscoutEntry()

        assertNotNull(entry)
        assertEquals("sgv", entry.type)
        assertEquals(140, entry.sgv)
        assertEquals(4, entry.trend)
        assertEquals("Flat", entry.direction)
    }

    @Test
    fun `toNightscoutEntry maps BGM measure to mbg entry`() {
        val data = buildJsonObject { put("value", 95) }
        val response = MeasureResponse(
            id = "m2",
            userId = "user1",
            measuredAt = "2024-01-01T01:00:00Z",
            createdAt = "2024-01-01T01:00:00Z",
            type = MeasureType.BGM,
            source = MeasureSource.MANUAL,
            data = data,
            status = MeasureStatus.ACTIVE,
        )

        val entry = response.toNightscoutEntry()

        assertNotNull(entry)
        assertEquals("mbg", entry.type)
        assertEquals(95, entry.sgv)
    }

    @Test
    fun `toNightscoutEntry returns null for BLOOD_PRESSURE measure`() {
        val response = MeasureResponse(
            id = "m3",
            userId = "user1",
            measuredAt = "2024-01-01T02:00:00Z",
            createdAt = "2024-01-01T02:00:00Z",
            type = MeasureType.BLOOD_PRESSURE,
            source = MeasureSource.MANUAL,
            data = buildJsonObject { put("systolic", 120) },
            status = MeasureStatus.ACTIVE,
        )

        val entry = response.toNightscoutEntry()

        assertNull(entry)
    }

    @Test
    fun `toNightscoutEntry returns null for malformed measuredAt timestamp`() {
        val response = MeasureResponse(
            id = "m4",
            userId = "user1",
            measuredAt = "not-a-valid-timestamp",
            createdAt = "2024-01-01T00:00:00Z",
            type = MeasureType.CGM,
            source = MeasureSource.NIGHTSCOUT,
            data = buildJsonObject { put("sgv", 140) },
            status = MeasureStatus.ACTIVE,
        )

        val entry = response.toNightscoutEntry()

        assertNull(entry)
    }

    @Test
    fun `toNightscoutEntry returns entry with null sgv when sgv field is missing`() {
        val response = MeasureResponse(
            id = "m5",
            userId = "user1",
            measuredAt = "2024-01-01T00:00:00Z",
            createdAt = "2024-01-01T00:00:00Z",
            type = MeasureType.CGM,
            source = MeasureSource.NIGHTSCOUT,
            data = buildJsonObject { put("trend", 2) },
            status = MeasureStatus.ACTIVE,
        )

        val entry = response.toNightscoutEntry()

        assertNotNull(entry)
        assertNull(entry.sgv)
    }

    // ---- TreatmentResponse.toNightscoutTreatment() ----

    @Test
    fun `toNightscoutTreatment maps BOLUS to eventType Bolus with insulin`() {
        val data = buildJsonObject { put("insulin", 3.0) }
        val response = TreatmentResponse(
            id = "t1",
            userId = "user1",
            treatedAt = "2024-01-01T08:00:00Z",
            createdAt = "2024-01-01T08:00:00Z",
            type = TreatmentType.BOLUS,
            data = data,
            status = TreatmentResponse.Status.ACTIVE,
            notes = null,
        )

        val treatment = response.toNightscoutTreatment()

        assertNotNull(treatment)
        assertEquals("Bolus", treatment.eventType)
        assertEquals(3.0, treatment.insulin)
    }

    @Test
    fun `toNightscoutTreatment maps CARBS to eventType Carbs with carbs`() {
        val data = buildJsonObject { put("carbs", 30.0) }
        val response = TreatmentResponse(
            id = "t2",
            userId = "user1",
            treatedAt = "2024-01-01T09:00:00Z",
            createdAt = "2024-01-01T09:00:00Z",
            type = TreatmentType.CARBS,
            data = data,
            status = TreatmentResponse.Status.ACTIVE,
            notes = null,
        )

        val treatment = response.toNightscoutTreatment()

        assertNotNull(treatment)
        assertEquals("Carbs", treatment.eventType)
        assertEquals(30.0, treatment.carbs)
    }

    @Test
    fun `toNightscoutTreatment maps SITE_CHANGE to eventType Site Change`() {
        val response = TreatmentResponse(
            id = "t3",
            userId = "user1",
            treatedAt = "2024-01-01T10:00:00Z",
            createdAt = "2024-01-01T10:00:00Z",
            type = TreatmentType.SITE_CHANGE,
            data = buildJsonObject {},
            status = TreatmentResponse.Status.ACTIVE,
            notes = null,
        )

        val treatment = response.toNightscoutTreatment()

        assertNotNull(treatment)
        assertEquals("Site Change", treatment.eventType)
    }

    @Test
    fun `toNightscoutTreatment returns null for malformed treatedAt timestamp`() {
        val response = TreatmentResponse(
            id = "t4",
            userId = "user1",
            treatedAt = "not-a-valid-timestamp",
            createdAt = "2024-01-01T00:00:00Z",
            type = TreatmentType.BOLUS,
            data = buildJsonObject { put("insulin", 2.0) },
            status = TreatmentResponse.Status.ACTIVE,
            notes = null,
        )

        val treatment = response.toNightscoutTreatment()

        assertNull(treatment)
    }

    // ---- NightscoutEntry.toMeasureRequest() ----

    @Test
    fun `toMeasureRequest maps sgv entry to CGM CreateMeasureRequest`() {
        val entry = NightscoutEntry(
            type = "sgv",
            sgv = 150,
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            trend = 4,
            direction = "Flat",
            id = "e1",
            mills = 1704067200000L,
        )

        val request = entry.toMeasureRequest()

        assertNotNull(request)
        assertEquals("2024-01-01T00:00:00Z", request.measuredAt)
    }

    @Test
    fun `toMeasureRequest maps mbg entry to BGM CreateMeasureRequest`() {
        val entry = NightscoutEntry(
            type = "mbg",
            sgv = 95,
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            id = "e2",
            mills = 1704067200000L,
        )

        val request = entry.toMeasureRequest()

        assertNotNull(request)
        assertEquals("2024-01-01T00:00:00Z", request.measuredAt)
    }

    @Test
    fun `toMeasureRequest returns null for unknown entry type`() {
        val entry = NightscoutEntry(
            type = "cal",
            sgv = null,
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            id = "e3",
            mills = 1704067200000L,
        )

        val request = entry.toMeasureRequest()

        assertNull(request)
    }

    // ---- NightscoutTreatment.toTreatmentRequest() ----

    @Test
    fun `toTreatmentRequest maps Bolus eventType to CreateTreatmentRequest`() {
        val treatment = NightscoutTreatment(
            id = "t1",
            eventType = "Bolus",
            createdAt = "2024-01-01T08:00:00Z",
            insulin = 3.5,
            mills = 1704067200000L,
        )

        val request = treatment.toTreatmentRequest()

        assertNotNull(request)
        assertEquals("2024-01-01T08:00:00Z", request.treatedAt)
    }

    @Test
    fun `toTreatmentRequest returns null for unrecognized eventType`() {
        val treatment = NightscoutTreatment(
            id = "t2",
            eventType = "Unknown Event",
            createdAt = "2024-01-01T08:00:00Z",
            mills = 1704067200000L,
        )

        val request = treatment.toTreatmentRequest()

        assertNull(request)
    }
}

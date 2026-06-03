package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureStatus
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NightscoutV3MapperTest {

    private fun cgmResponse(sgv: Int? = 120, direction: String? = null): MeasureResponse {
        val data = buildJsonObject {
            sgv?.let { put("sgv", it) }
            direction?.let { put("direction", it) }
        }
        return MeasureResponse(
            id = "m1",
            userId = "user1",
            measuredAt = "2024-01-01T00:00:00Z",
            createdAt = "2024-01-01T00:00:00Z",
            type = MeasureType.CGM,
            source = MeasureSource.NIGHTSCOUT,
            data = data,
            status = MeasureStatus.ACTIVE,
        )
    }

    // ── MeasureResponse.toNs3Entry() ──────────────────────────────────────────

    @Test
    fun `toNs3Entry passes through sgv unchanged for mg-dL`() {
        val response = cgmResponse(sgv = 120)
        val entry = response.toNs3Entry("mg/dL")
        assertEquals(120.0, entry.sgv)
        assertEquals("sgv", entry.type)
        assertEquals("m1", entry.identifier)
    }

    @Test
    fun `toNs3Entry converts sgv to mmol-L when glucoseUnit is mmol-L`() {
        // 120 mg/dL / 18.0 * 10 = 66.67 -> round to 67 -> / 10 = 6.7
        val response = cgmResponse(sgv = 120)
        val entry = response.toNs3Entry("mmol/L")
        assertEquals(6.7, entry.sgv)
    }

    @Test
    fun `toNs3Entry returns null sgv when data has no sgv field`() {
        val response = MeasureResponse(
            id = "m2",
            userId = "user1",
            measuredAt = "2024-01-01T00:00:00Z",
            createdAt = "2024-01-01T00:00:00Z",
            type = MeasureType.CGM,
            source = MeasureSource.NIGHTSCOUT,
            data = buildJsonObject {},
            status = MeasureStatus.ACTIVE,
        )
        val entry = response.toNs3Entry("mg/dL")
        assertNull(entry.sgv)
    }

    @Test
    fun `toNs3Entry maps BGM type to mbg`() {
        val response = MeasureResponse(
            id = "m3",
            userId = "user1",
            measuredAt = "2024-01-01T00:00:00Z",
            createdAt = "2024-01-01T00:00:00Z",
            type = MeasureType.BGM,
            source = MeasureSource.MANUAL,
            data = buildJsonObject { put("sgv", 110) },
            status = MeasureStatus.ACTIVE,
        )
        val entry = response.toNs3Entry("mg/dL")
        assertEquals("mbg", entry.type)
    }

    @Test
    fun `toNs3Entry extracts direction from data`() {
        val response = cgmResponse(sgv = 130, direction = "FortyFiveUp")
        val entry = response.toNs3Entry("mg/dL")
        assertEquals("FortyFiveUp", entry.direction)
    }

    @Test
    fun `toNs3Entry uses value field when sgv field is absent`() {
        val response = MeasureResponse(
            id = "m5",
            userId = "user1",
            measuredAt = "2024-01-01T00:00:00Z",
            createdAt = "2024-01-01T00:00:00Z",
            type = MeasureType.CGM,
            source = MeasureSource.NIGHTSCOUT,
            data = buildJsonObject { put("value", 100) },
            status = MeasureStatus.ACTIVE,
        )
        val entry = response.toNs3Entry("mg/dL")
        assertEquals(100.0, entry.sgv)
    }

    @Test
    fun `toNs3Entry sets srvCreated and srvModified to same epoch millis as date`() {
        val response = cgmResponse(sgv = 100)
        val entry = response.toNs3Entry("mg/dL")
        assertEquals(entry.date, entry.srvCreated)
        assertEquals(entry.date, entry.srvModified)
    }

    @Test
    fun `toNs3Entry sets date from measuredAt epoch millis`() {
        val response = cgmResponse(sgv = 100)
        // 2024-01-01T00:00:00Z → 1704067200000L
        val entry = response.toNs3Entry("mg/dL")
        assertEquals(1704067200000L, entry.date)
        assertEquals("2024-01-01T00:00:00Z", entry.dateString)
    }

    @Test
    fun `toNs3Entry returns 0 date for malformed measuredAt timestamp`() {
        val response = MeasureResponse(
            id = "m-bad",
            userId = "user1",
            measuredAt = "not-a-timestamp",
            createdAt = "2024-01-01T00:00:00Z",
            type = MeasureType.CGM,
            source = MeasureSource.NIGHTSCOUT,
            data = buildJsonObject { put("sgv", 100) },
            status = MeasureStatus.ACTIVE,
        )
        val entry = response.toNs3Entry("mg/dL")
        assertEquals(0L, entry.date)
    }

    // ── Ns3Entry.toCreateMeasureRequest() ────────────────────────────────────

    @Test
    fun `toCreateMeasureRequest maps sgv entry to CGM type`() {
        val entry = Ns3Entry(
            identifier = "e1",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 140.0,
        )
        val request = entry.toCreateMeasureRequest("mg/dL")
        assertNotNull(request)
        assertEquals(MeasureType.CGM, request.type)
        assertEquals("2024-01-01T00:00:00Z", request.measuredAt)
    }

    @Test
    fun `toCreateMeasureRequest maps mbg entry to BGM type`() {
        val entry = Ns3Entry(
            identifier = "e2",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "mbg",
            sgv = 95.0,
        )
        val request = entry.toCreateMeasureRequest("mg/dL")
        assertNotNull(request)
        assertEquals(MeasureType.BGM, request.type)
    }

    @Test
    fun `toCreateMeasureRequest returns null for unknown entry type`() {
        val entry = Ns3Entry(
            identifier = "e3",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "cal",
            sgv = null,
        )
        val request = entry.toCreateMeasureRequest("mg/dL")
        assertNull(request)
    }

    @Test
    fun `toCreateMeasureRequest stores sgv as integer mgdl when unit is mg-dL`() {
        val entry = Ns3Entry(
            identifier = "e4",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 180.0,
        )
        val request = entry.toCreateMeasureRequest("mg/dL")
        assertNotNull(request)
        // sgv 180.0 mg/dL → stored as int 180 in data["sgv"]
        assertNotNull(request.data["sgv"])
    }

    @Test
    fun `toCreateMeasureRequest converts mmol-L to mgdl before storing`() {
        // 6.7 mmol/L * 18 = 120.6 → stored as int 120
        val entry = Ns3Entry(
            identifier = "e5",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 6.7,
        )
        val request = entry.toCreateMeasureRequest("mmol/L")
        assertNotNull(request)
        assertEquals(120, request.data["sgv"]?.jsonPrimitive?.int)
    }

    @Test
    fun `toCreateMeasureRequest omits sgv from data when sgv is null`() {
        val entry = Ns3Entry(
            identifier = "e6",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = null,
        )
        val request = entry.toCreateMeasureRequest("mg/dL")
        assertNotNull(request)
        assertNull(request.data["sgv"])
    }

    @Test
    fun `toCreateMeasureRequest includes direction in data when present`() {
        val entry = Ns3Entry(
            identifier = "e7",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 120.0,
            direction = "Flat",
        )
        val request = entry.toCreateMeasureRequest("mg/dL")
        assertNotNull(request)
        assertNotNull(request.data["direction"])
    }

    // ── Ns3Entry.toUpdateMeasureRequest() ────────────────────────────────────

    @Test
    fun `toUpdateMeasureRequest sets measuredAt from dateString`() {
        val entry = Ns3Entry(
            identifier = "e8",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 150.0,
        )
        val request = entry.toUpdateMeasureRequest("mg/dL")
        assertEquals("2024-01-01T00:00:00Z", request.measuredAt)
    }

    @Test
    fun `toUpdateMeasureRequest converts mmol-L to mgdl`() {
        val entry = Ns3Entry(
            identifier = "e9",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 7.8,
        )
        val request = entry.toUpdateMeasureRequest("mmol/L")
        // 7.8 * 18 = 140.4 → stored as int 140
        assertEquals(140, request.data["sgv"]?.jsonPrimitive?.int)
    }

    @Test
    fun `toUpdateMeasureRequest omits sgv from data when sgv is null`() {
        val entry = Ns3Entry(
            identifier = "e10",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = null,
        )
        val request = entry.toUpdateMeasureRequest("mg/dL")
        assertNull(request.data["sgv"])
    }

    @Test
    fun `toUpdateMeasureRequest includes direction when present`() {
        val entry = Ns3Entry(
            identifier = "e11",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 130.0,
            direction = "FortyFiveDown",
        )
        val request = entry.toUpdateMeasureRequest("mg/dL")
        assertNotNull(request.data["direction"])
    }

    @Test
    fun `toUpdateMeasureRequest omits direction when absent`() {
        val entry = Ns3Entry(
            identifier = "e12",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 130.0,
            direction = null,
        )
        val request = entry.toUpdateMeasureRequest("mg/dL")
        assertNull(request.data["direction"])
    }
}

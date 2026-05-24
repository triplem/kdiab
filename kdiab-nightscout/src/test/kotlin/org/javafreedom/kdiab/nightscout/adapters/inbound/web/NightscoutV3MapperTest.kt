package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureStatus
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NightscoutV3MapperTest {

    private fun cgmResponse(sgv: Int?): MeasureResponse {
        val data = buildJsonObject {
            sgv?.let { put("sgv", it) }
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
            data = buildJsonObject { },
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
        val response = MeasureResponse(
            id = "m4",
            userId = "user1",
            measuredAt = "2024-01-01T00:00:00Z",
            createdAt = "2024-01-01T00:00:00Z",
            type = MeasureType.CGM,
            source = MeasureSource.NIGHTSCOUT,
            data = buildJsonObject {
                put("sgv", 130)
                put("direction", "FortyFiveUp")
            },
            status = MeasureStatus.ACTIVE,
        )
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
}

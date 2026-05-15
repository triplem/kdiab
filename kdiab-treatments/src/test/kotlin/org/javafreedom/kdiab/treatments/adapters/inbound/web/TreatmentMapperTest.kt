@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.treatments.api.models.CreateTreatmentRequest
import org.javafreedom.kdiab.treatments.api.models.TreatmentType as ApiType
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType

class TreatmentMapperTest {

    private val userId      = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val treatmentId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val data        = buildJsonObject { put("insulin", 2.5) }

    // ── CreateTreatmentRequest.toDomain ───────────────────────────────────────

    @Test
    fun `toDomain maps all fields from CreateTreatmentRequest`() {
        val request = CreateTreatmentRequest(
            treatedAt = "2024-01-01T10:00:00Z",
            type = ApiType.BOLUS,
            data = data
        )
        val domain = request.toDomain(userId)

        assertNotNull(domain.id)
        assertEquals(userId, domain.userId)
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), domain.treatedAt)
        assertEquals(TreatmentType.BOLUS, domain.type)
        assertEquals(data, domain.data)
        assertNull(domain.notes)
    }

    @Test
    fun `toDomain maps optional notes field`() {
        val request = CreateTreatmentRequest(
            treatedAt = "2024-01-01T10:00:00Z",
            type = ApiType.BOLUS,
            data = data,
            notes = "felt low before dinner"
        )
        val domain = request.toDomain(userId)
        assertEquals("felt low before dinner", domain.notes)
    }

    @Test
    fun `toDomain maps all TreatmentType values correctly`() {
        TreatmentType.entries.forEach { domainType ->
            val request = CreateTreatmentRequest(
                treatedAt = "2024-01-01T10:00:00Z",
                type = ApiType.valueOf(domainType.name),
                data = data
            )
            assertEquals(domainType, request.toDomain(userId).type)
        }
    }

    // ── DomainTreatment.toApi ─────────────────────────────────────────────────

    private fun testDomainTreatment(
        type: TreatmentType = TreatmentType.BOLUS,
        notes: String? = null
    ) = Treatment(
        id        = treatmentId,
        userId    = userId,
        treatedAt = Instant.parse("2024-01-01T10:00:00Z"),
        createdAt = Instant.parse("2024-01-01T10:00:01Z"),
        type  = type,
        data  = data,
        notes = notes,
    )

    @Test
    fun `toApi maps all fields from DomainTreatment`() {
        val api = testDomainTreatment().toApi()

        assertEquals(treatmentId.toString(), api.id)
        assertEquals(userId.toString(), api.userId)
        assertEquals("2024-01-01T10:00:00Z", api.treatedAt)
        assertEquals("2024-01-01T10:00:01Z", api.createdAt)
        assertEquals(ApiType.BOLUS, api.type)
        assertEquals(data, api.data)
        assertNull(api.notes)
    }

    @Test
    fun `toApi maps notes field`() {
        val api = testDomainTreatment(notes = "post-meal correction").toApi()
        assertEquals("post-meal correction", api.notes)
    }

    @Test
    fun `toApi maps all TreatmentType values correctly`() {
        TreatmentType.entries.forEach { domainType ->
            val api = testDomainTreatment(type = domainType).toApi()
            assertEquals(ApiType.valueOf(domainType.name), api.type)
        }
    }

    @Test
    fun `toDomain maps DEVICE_STATUS treatment with device status payload`() {
        val payload = buildJsonObject {
            put("device", "AAPS 3.2.0")
            put("pumpName", "Dana RS")
            put("reservoirUnits", 142.5)
            put("batteryLevel", 87)
            put("pumpConnected", true)
        }
        val request = CreateTreatmentRequest(
            treatedAt = "2024-01-01T10:00:00Z",
            type = ApiType.DEVICE_STATUS,
            data = payload
        )
        val domain = request.toDomain(userId)

        assertEquals(TreatmentType.DEVICE_STATUS, domain.type)
        assertEquals("AAPS 3.2.0", (domain.data["device"] as JsonPrimitive).content)
        assertEquals("Dana RS", (domain.data["pumpName"] as JsonPrimitive).content)
        assertEquals(142.5, (domain.data["reservoirUnits"] as JsonPrimitive).double, 0.001)
        assertEquals(87, (domain.data["batteryLevel"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun `toApi maps DEVICE_STATUS treatment back to api type`() {
        val payload = buildJsonObject {
            put("device", "xDrip+ 2024.01.15")
            put("pumpName", "Omnipod 5")
            put("reservoirUnits", 200.0)
            put("batteryLevel", 95)
            put("pumpConnected", true)
        }
        val api = testDomainTreatment(type = TreatmentType.DEVICE_STATUS).copy(data = payload).toApi()

        assertEquals(ApiType.DEVICE_STATUS, api.type)
        assertEquals("xDrip+ 2024.01.15", (api.data["device"] as JsonPrimitive).content)
    }

    // ── normalizePayload ──────────────────────────────────────────────────────

    @Test
    fun `normalizePayload converts duration hours to minutes`() {
        val input = buildJsonObject {
            put("duration", 24)
            put("durationUnit", "hours")
        }
        val result = normalizePayload(input)
        assertEquals(1440L, (result["duration"] as JsonPrimitive).long)
        assertEquals(null, result["durationUnit"])
    }

    @Test
    fun `normalizePayload converts duration seconds to minutes`() {
        val input = buildJsonObject {
            put("duration", 120)
            put("durationUnit", "seconds")
        }
        val result = normalizePayload(input)
        // 120 seconds → 2 minutes
        assertEquals(2L, (result["duration"] as JsonPrimitive).long)
        assertEquals(null, result["durationUnit"])
    }

    @Test
    fun `normalizePayload leaves duration unchanged when unit field absent`() {
        val input = buildJsonObject { put("duration", 60) }
        val result = normalizePayload(input)
        assertEquals(60L, (result["duration"] as JsonPrimitive).long)
    }

    @Test
    fun `normalizePayload converts glucose mmol to mgdl`() {
        val input = buildJsonObject {
            put("glucose", 5.5)
            put("units", "mmol")
        }
        val result = normalizePayload(input)
        val stored = (result["glucose"] as JsonPrimitive).double
        assertEquals(99.1, stored, 0.1)
        assertEquals(null, result["units"])
    }

    @Test
    fun `normalizePayload converts glucose mmol_l to mgdl`() {
        val input = buildJsonObject {
            put("glucose", 10.0)
            put("units", "mmol/l")
        }
        val result = normalizePayload(input)
        val stored = (result["glucose"] as JsonPrimitive).double
        assertEquals(180.182, stored, 0.01)
        assertEquals(null, result["units"])
    }

    @Test
    fun `normalizePayload leaves glucose unchanged when already mgdl`() {
        val input = buildJsonObject {
            put("glucose", 100.0)
            put("units", "mgdl")
        }
        val result = normalizePayload(input)
        assertEquals(100L, (result["glucose"] as JsonPrimitive).long)
        assertEquals(null, result["units"])
    }

    @Test
    fun `normalizePayload leaves glucose unchanged when unit field absent`() {
        val input = buildJsonObject { put("glucose", 5.6) }
        val result = normalizePayload(input)
        assertEquals(5.6, (result["glucose"] as JsonPrimitive).double, 0.001)
    }

    @Test
    fun `normalizePayload converts absorptionTime minutes to hours`() {
        val input = buildJsonObject {
            put("absorptionTime", 180)
            put("absorptionTimeUnit", "minutes")
        }
        val result = normalizePayload(input)
        assertEquals(3.0, (result["absorptionTime"] as JsonPrimitive).double, 0.001)
        assertEquals(null, result["absorptionTimeUnit"])
    }

    @Test
    fun `normalizePayload leaves absorptionTime unchanged when unit field absent`() {
        val input = buildJsonObject { put("absorptionTime", 3) }
        val result = normalizePayload(input)
        assertEquals(3L, (result["absorptionTime"] as JsonPrimitive).long)
    }

    @Test
    fun `normalizePayload ignores unknown unit values safely`() {
        val input = buildJsonObject {
            put("duration", 30)
            put("durationUnit", "parsecs")
        }
        val result = normalizePayload(input)
        // Unknown unit → field left as-is, unit field retained
        assertEquals(30L, (result["duration"] as JsonPrimitive).long)
        assertEquals("parsecs", (result["durationUnit"] as JsonPrimitive).content)
    }

    @Test
    fun `normalizePayload passes through unregistered fields untouched`() {
        val input = buildJsonObject {
            put("insulin", 2.5)
            put("insulinType", "NovoRapid")
        }
        val result = normalizePayload(input)
        assertEquals(2.5, (result["insulin"] as JsonPrimitive).double, 0.001)
        assertEquals("NovoRapid", (result["insulinType"] as JsonPrimitive).content)
    }

    @Test
    fun `normalizePayload unit comparison is case-insensitive`() {
        val input = buildJsonObject {
            put("duration", 1)
            put("durationUnit", "HOURS")
        }
        val result = normalizePayload(input)
        assertEquals(60L, (result["duration"] as JsonPrimitive).long)
        assertEquals(null, result["durationUnit"])
    }
}

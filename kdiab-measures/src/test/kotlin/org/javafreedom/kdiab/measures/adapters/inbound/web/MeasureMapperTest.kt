@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.measures.api.models.CreateMeasureRequest
import org.javafreedom.kdiab.measures.api.models.MeasureSource as ApiSource
import org.javafreedom.kdiab.measures.api.models.MeasureStatus as ApiStatus
import org.javafreedom.kdiab.measures.api.models.MeasureType as ApiType
import org.javafreedom.kdiab.measures.domain.model.Measure
import org.javafreedom.kdiab.measures.domain.model.MeasureSource
import org.javafreedom.kdiab.measures.domain.model.MeasureStatus
import org.javafreedom.kdiab.measures.domain.model.MeasureType

class MeasureMapperTest {

    private val userId    = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val measureId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val data      = buildJsonObject { put("mbg", 120) }

    // ── CreateMeasureRequest.toDomain ─────────────────────────────────────────

    @Test
    fun `toDomain maps all fields from CreateMeasureRequest`() {
        val request = CreateMeasureRequest(
            measuredAt = "2024-01-01T10:00:00Z",
            type   = ApiType.BGM,
            source = ApiSource.MANUAL,
            data   = data
        )
        val domain = request.toDomain(userId)

        assertNotNull(domain.id)
        assertEquals(userId, domain.userId)
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), domain.measuredAt)
        assertEquals(MeasureType.BGM, domain.type)
        assertEquals(MeasureSource.MANUAL, domain.source)
        assertEquals(data, domain.data)
        assertEquals(MeasureStatus.ACTIVE, domain.status)
    }

    @Test
    fun `toDomain maps all MeasureType values correctly`() {
        MeasureType.entries.forEach { domainType ->
            val request = CreateMeasureRequest(
                measuredAt = "2024-01-01T10:00:00Z",
                type   = ApiType.valueOf(domainType.name),
                source = ApiSource.MANUAL,
                data   = data
            )
            assertEquals(domainType, request.toDomain(userId).type)
        }
    }

    @Test
    fun `toDomain maps all MeasureSource values correctly`() {
        MeasureSource.entries.forEach { domainSource ->
            val request = CreateMeasureRequest(
                measuredAt = "2024-01-01T10:00:00Z",
                type   = ApiType.BGM,
                source = ApiSource.valueOf(domainSource.name),
                data   = data
            )
            assertEquals(domainSource, request.toDomain(userId).source)
        }
    }

    // ── DomainMeasure.toApi ───────────────────────────────────────────────────

    private fun testDomainMeasure(
        type: MeasureType = MeasureType.BGM,
        source: MeasureSource = MeasureSource.MANUAL,
        status: MeasureStatus = MeasureStatus.ACTIVE
    ) = Measure(
        id         = measureId,
        userId     = userId,
        measuredAt = Instant.parse("2024-01-01T10:00:00Z"),
        createdAt  = Instant.parse("2024-01-01T10:00:01Z"),
        type   = type,
        source = source,
        data   = data,
        status = status
    )

    @Test
    fun `toApi maps all fields from DomainMeasure`() {
        val api = testDomainMeasure().toApi()

        assertEquals(measureId.toString(), api.id)
        assertEquals(userId.toString(), api.userId)
        assertEquals("2024-01-01T10:00:00Z", api.measuredAt)
        assertEquals("2024-01-01T10:00:01Z", api.createdAt)
        assertEquals(ApiType.BGM, api.type)
        assertEquals(ApiSource.MANUAL, api.source)
        assertEquals(data, api.data)
        assertEquals(ApiStatus.ACTIVE, api.status)
    }

    @Test
    fun `toApi maps ARCHIVED status correctly`() {
        val api = testDomainMeasure(status = MeasureStatus.ARCHIVED).toApi()
        assertEquals(ApiStatus.ARCHIVED, api.status)
    }

    @Test
    fun `toApi maps all MeasureType values correctly`() {
        MeasureType.entries.forEach { domainType ->
            val api = testDomainMeasure(type = domainType).toApi()
            assertEquals(ApiType.valueOf(domainType.name), api.type)
        }
    }

    @Test
    fun `toApi maps all MeasureSource values correctly`() {
        MeasureSource.entries.forEach { domainSource ->
            val api = testDomainMeasure(source = domainSource).toApi()
            assertEquals(ApiSource.valueOf(domainSource.name), api.source)
        }
    }

    // ── normalizeToCanonical: mmol/L → mg/dL conversion ──────────────────────

    @Test
    fun `toDomain normalizes glucose from mmol-L to mg-dL`() {
        val mmolData = buildJsonObject {
            put("value", 5.5)
            put("unit", "mmol/L")
        }
        val request = CreateMeasureRequest(
            measuredAt = "2024-01-01T10:00:00Z",
            type = ApiType.BGM,
            source = ApiSource.MANUAL,
            data = mmolData
        )
        val domain = request.toDomain(userId)
        val storedValue = domain.data["value"]?.jsonPrimitive?.double
        assertNotNull(storedValue)
        // 5.5 mmol/L × 18.0182 = 99.1001 → rounded to nearest integer → 99
        assertTrue(storedValue in 98.0..100.0, "Expected ~99 mg/dL, got $storedValue")
        // unit key must not be stored in canonical form
        assertEquals(null, domain.data["unit"])
    }

    @Test
    fun `toDomain normalizes weight from lbs to kg`() {
        val lbsData = buildJsonObject {
            put("value", 154.3)
            put("unit", "lbs")
        }
        val request = CreateMeasureRequest(
            measuredAt = "2024-01-01T10:00:00Z",
            type = ApiType.WEIGHT,
            source = ApiSource.MANUAL,
            data = lbsData
        )
        val domain = request.toDomain(userId)
        val storedValue = domain.data["value"]?.jsonPrimitive?.double
        assertNotNull(storedValue)
        // 154.3 lbs ÷ 2.20462 = 69.99... → rounded to 1 decimal → 70.0
        assertTrue(storedValue in 69.5..70.5, "Expected ~70.0 kg, got $storedValue")
    }

    @Test
    fun `toDomain preserves glucose value already in mg-dL`() {
        val mgdlData = buildJsonObject {
            put("value", 120.0)
            put("unit", "mg/dL")
        }
        val request = CreateMeasureRequest(
            measuredAt = "2024-01-01T10:00:00Z",
            type = ApiType.BGM,
            source = ApiSource.MANUAL,
            data = mgdlData
        )
        val domain = request.toDomain(userId)
        assertEquals(120.0, domain.data["value"]?.jsonPrimitive?.double)
    }

    @Test
    fun `toDomain preserves weight value already in kg`() {
        val kgData = buildJsonObject {
            put("value", 70.0)
            put("unit", "kg")
        }
        val request = CreateMeasureRequest(
            measuredAt = "2024-01-01T10:00:00Z",
            type = ApiType.WEIGHT,
            source = ApiSource.MANUAL,
            data = kgData
        )
        val domain = request.toDomain(userId)
        assertEquals(70.0, domain.data["value"]?.jsonPrimitive?.double)
    }

    // ── convertFromCanonical: mg/dL → mmol/L and kg → lbs ────────────────────

    @Test
    fun `toApi converts glucose from mg-dL to mmol-L when glucoseUnit is mmol-L`() {
        val mgdlData = buildJsonObject { put("value", 99.0) }
        val measure = testDomainMeasure(type = MeasureType.BGM).copy(data = mgdlData)
        val api = measure.toApi(glucoseUnit = "mmol/L")
        val convertedValue = api.data["value"]?.jsonPrimitive?.double
        // 99 / 18.0182 * 10 rounded / 10 ≈ 5.5
        assertNotNull(convertedValue)
        assertEquals(5.5, convertedValue)
        assertEquals("mmol/L", api.data["unit"]?.jsonPrimitive?.content)
    }

    @Test
    fun `toApi rounds glucose value in mg-dL when glucoseUnit is mg-dL`() {
        val mgdlData = buildJsonObject { put("value", 99.4) }
        val measure = testDomainMeasure(type = MeasureType.BGM).copy(data = mgdlData)
        val api = measure.toApi(glucoseUnit = "mg/dL")
        val roundedValue = api.data["value"]?.jsonPrimitive?.double
        assertEquals(99.0, roundedValue)
        assertEquals("mg/dL", api.data["unit"]?.jsonPrimitive?.content)
    }

    @Test
    fun `toApi converts weight from kg to lbs when weightUnit is lbs`() {
        val kgData = buildJsonObject { put("value", 70.0) }
        val measure = testDomainMeasure(type = MeasureType.WEIGHT).copy(data = kgData)
        val api = measure.toApi(weightUnit = "lbs")
        val convertedValue = api.data["value"]?.jsonPrimitive?.double
        // 70 * 2.20462 * 10 rounded / 10 ≈ 154.3
        assertNotNull(convertedValue)
        assertEquals(154.3, convertedValue)
        assertEquals("lbs", api.data["unit"]?.jsonPrimitive?.content)
    }

    @Test
    fun `toApi rounds weight value in kg when weightUnit is kg`() {
        val kgData = buildJsonObject { put("value", 70.12) }
        val measure = testDomainMeasure(type = MeasureType.WEIGHT).copy(data = kgData)
        val api = measure.toApi(weightUnit = "kg")
        val roundedValue = api.data["value"]?.jsonPrimitive?.double
        assertEquals(70.1, roundedValue)
        assertEquals("kg", api.data["unit"]?.jsonPrimitive?.content)
    }

    @Test
    fun `toDomain throws BusinessValidationException for invalid measuredAt`() {
        val request = CreateMeasureRequest(
            measuredAt = "not-a-valid-timestamp",
            type = ApiType.BGM,
            source = ApiSource.MANUAL,
            data = data
        )
        kotlin.test.assertFailsWith<BusinessValidationException> {
            request.toDomain(userId)
        }
    }
}

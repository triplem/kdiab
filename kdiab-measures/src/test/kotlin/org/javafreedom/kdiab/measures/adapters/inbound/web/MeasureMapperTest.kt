@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
}

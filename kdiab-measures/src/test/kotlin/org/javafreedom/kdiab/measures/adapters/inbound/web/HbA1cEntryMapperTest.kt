@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.measures.api.models.CreateHba1cEntryRequest
import org.javafreedom.kdiab.measures.api.models.HbA1cSource as ApiHbA1cSource
import org.javafreedom.kdiab.measures.domain.model.HbA1cEntry
import org.javafreedom.kdiab.measures.domain.model.HbA1cSource

class HbA1cEntryMapperTest {

    private val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val entryId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val now = Instant.parse("2024-06-01T10:00:00Z")

    private fun testDomainEntry() = HbA1cEntry(
        id = entryId,
        userId = userId,
        measuredAt = now,
        valuePercent = 6.5,
        source = HbA1cSource.LAB,
        notes = "Annual checkup",
        createdAt = now,
    )

    @Test
    fun `toDomain maps all fields from CreateHba1cEntryRequest`() {
        val request = CreateHba1cEntryRequest(
            measuredAt = "2024-06-01T10:00:00Z",
            valuePercent = 6.5,
            source = ApiHbA1cSource.LAB,
            notes = "Annual checkup",
        )
        val domain = request.toDomain(userId)

        assertNotNull(domain.id)
        assertEquals(userId, domain.userId)
        assertEquals(Instant.parse("2024-06-01T10:00:00Z"), domain.measuredAt)
        assertEquals(6.5, domain.valuePercent)
        assertEquals(HbA1cSource.LAB, domain.source)
        assertEquals("Annual checkup", domain.notes)
        assertNotNull(domain.createdAt)
    }

    @Test
    fun `toDomain defaults source to LAB when null`() {
        val request = CreateHba1cEntryRequest(
            measuredAt = "2024-06-01T10:00:00Z",
            valuePercent = 7.2,
            source = null,
            notes = null,
        )
        val domain = request.toDomain(userId)
        assertEquals(HbA1cSource.LAB, domain.source)
        assertNull(domain.notes)
    }

    @Test
    fun `toDomain maps CGM_ESTIMATED source`() {
        val request = CreateHba1cEntryRequest(
            measuredAt = "2024-06-01T10:00:00Z",
            valuePercent = 7.0,
            source = ApiHbA1cSource.CGM_ESTIMATED,
            notes = null,
        )
        assertEquals(HbA1cSource.CGM_ESTIMATED, request.toDomain(userId).source)
    }

    @Test
    fun `toDomain throws BusinessValidationException for invalid measuredAt`() {
        val request = CreateHba1cEntryRequest(
            measuredAt = "not-a-date",
            valuePercent = 6.5,
            source = null,
            notes = null,
        )
        assertFailsWith<BusinessValidationException> {
            request.toDomain(userId)
        }
    }

    @Test
    fun `toApi maps all fields from HbA1cEntry`() {
        val api = testDomainEntry().toApi()

        assertEquals(entryId.toString(), api.id)
        assertEquals(userId.toString(), api.userId)
        assertEquals("2024-06-01T10:00:00Z", api.measuredAt)
        assertEquals(6.5, api.valuePercent)
        assertEquals(ApiHbA1cSource.LAB, api.source)
        assertEquals("Annual checkup", api.notes)
        assertEquals("2024-06-01T10:00:00Z", api.createdAt)
    }

    @Test
    fun `toApi maps null notes correctly`() {
        val entry = testDomainEntry().copy(notes = null)
        val api = entry.toApi()
        assertNull(api.notes)
    }

    @Test
    fun `toApi maps CGM_ESTIMATED source`() {
        val entry = testDomainEntry().copy(source = HbA1cSource.CGM_ESTIMATED)
        val api = entry.toApi()
        assertEquals(ApiHbA1cSource.CGM_ESTIMATED, api.source)
    }
}

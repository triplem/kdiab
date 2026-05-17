@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.analyze.application.port.outbound.MeasuresPort
import org.javafreedom.kdiab.analyze.application.port.outbound.TreatmentsPort
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureStatus
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentType
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TimelineServiceTest {

    private val measuresClient = mockk<MeasuresPort>()
    private val treatmentsClient = mockk<TreatmentsPort>()
    private val service = TimelineService(measuresClient, treatmentsClient)

    private val userId = "11111111-1111-1111-1111-111111111111"
    private val auth = "Bearer token"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-31T23:59:59Z"

    private val insideId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    private val beforeId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    private val afterId  = "cccccccc-cccc-cccc-cccc-cccccccccccc"
    private val measureId1 = "11111111-2222-3333-4444-555555555555"
    private val treatmentId1 = "66666666-7777-8888-9999-000000000000"

    private fun measure(id: String, at: String = "2024-01-15T12:00:00Z") = MeasureResponse(
        id = id, userId = userId, measuredAt = at, createdAt = at,
        type = MeasureType.CGM, source = MeasureSource.MANUAL,
        `data` = buildJsonObject { put("value", 120.0); put("unit", "mg/dL") },
        status = MeasureStatus.ACTIVE,
    )

    private fun treatment(id: String, at: String = "2024-01-15T13:00:00Z") = TreatmentResponse(
        id = id, userId = userId, treatedAt = at, createdAt = at,
        type = TreatmentType.BOLUS,
        `data` = buildJsonObject { put("insulin", 4.0) },
        status = TreatmentResponse.Status.ACTIVE,
    )

    @Test
    fun `getTimeline returns empty lists when no data`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getTimeline(userId, from, to, auth, "")
        assertTrue(result.measures.isEmpty())
        assertTrue(result.treatments.isEmpty())
    }

    @Test
    fun `getTimeline filters measures outside timeframe`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            measure(insideId, "2024-01-15T12:00:00Z"),
            measure(beforeId, "2023-12-31T23:59:00Z"),
            measure(afterId, "2024-02-01T00:00:01Z"),
        )
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getTimeline(userId, from, to, auth, "")
        assertEquals(1, result.measures.size)
        assertEquals(Uuid.parse(insideId), result.measures.first().id)
    }

    @Test
    fun `getTimeline filters treatments outside timeframe`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns listOf(
            treatment(insideId, "2024-01-20T08:00:00Z"),
            treatment(beforeId, "2023-12-01T00:00:00Z"),
        )
        val result = service.getTimeline(userId, from, to, auth, "")
        assertEquals(1, result.treatments.size)
        assertEquals(Uuid.parse(insideId), result.treatments.first().id)
    }

    @Test
    fun `getTimeline includes boundary instants`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            measure(insideId, from),
            measure(beforeId, to),
        )
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getTimeline(userId, from, to, auth, "")
        assertEquals(2, result.measures.size)
    }

    @Test
    fun `getTimeline maps measure fields correctly`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(measure(measureId1))
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getTimeline(userId, from, to, auth, "")
        val m = result.measures.first()
        assertEquals(Uuid.parse(measureId1), m.id)
        assertEquals(Uuid.parse(userId), m.userId)
        assertEquals("CGM", m.type)
        assertEquals("ACTIVE", m.status)
    }

    @Test
    fun `getTimeline maps treatment fields correctly`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns listOf(
            TreatmentResponse(
                id = treatmentId1, userId = userId, treatedAt = "2024-01-10T08:00:00Z",
                createdAt = "2024-01-10T08:00:00Z",
                type = TreatmentType.BOLUS, notes = "breakfast",
                `data` = buildJsonObject { put("insulin", 6.0) },
                status = TreatmentResponse.Status.ACTIVE,
            )
        )
        val result = service.getTimeline(userId, from, to, auth, "")
        val t = result.treatments.first()
        assertEquals(Uuid.parse(treatmentId1), t.id)
        assertEquals("BOLUS", t.type)
        assertEquals("breakfast", t.notes)
    }

    @Test
    fun `getTimeline returns partial data with errors when both upstreams fail`() = runTest {
        coEvery {
            measuresClient.getMeasures(any(), any(), any(), any(), any())
        } throws UpstreamException("measures", 500, "down")
        coEvery {
            treatmentsClient.getTreatments(any(), any(), any(), any(), any())
        } throws UpstreamException("treatments", 500, "down")

        val result = service.getTimeline(userId, from, to, auth, "")

        assertTrue(result.measures.isEmpty())
        assertTrue(result.treatments.isEmpty())
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.any { it.startsWith("measures:") })
        assertTrue(result.errors.any { it.startsWith("treatments:") })
    }

    @Test
    fun `getTimeline returns treatments with error when only measures fails`() = runTest {
        coEvery {
            measuresClient.getMeasures(any(), any(), any(), any(), any())
        } throws UpstreamException("measures", 500, "down")
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns listOf(treatment(treatmentId1))

        val result = service.getTimeline(userId, from, to, auth, "")

        assertTrue(result.measures.isEmpty())
        assertEquals(1, result.treatments.size)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().startsWith("measures:"))
    }

    @Test
    fun `getTimeline returns measures with error when only treatments fails`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(measure(measureId1))
        coEvery {
            treatmentsClient.getTreatments(any(), any(), any(), any(), any())
        } throws UpstreamException("treatments", 500, "down")

        val result = service.getTimeline(userId, from, to, auth, "")

        assertEquals(1, result.measures.size)
        assertTrue(result.treatments.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().startsWith("treatments:"))
    }

    @Test
    fun `getTimeline silently drops measures with invalid UUID fields`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            measure("not-a-uuid", "2024-01-15T12:00:00Z"),
            measure(measureId1, "2024-01-15T12:00:00Z"),
        )
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getTimeline(userId, from, to, auth, "")
        assertEquals(1, result.measures.size)
        assertEquals(Uuid.parse(measureId1), result.measures.first().id)
    }
}

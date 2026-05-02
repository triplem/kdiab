package org.javafreedom.kdiab.bff.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.bff.adapters.outbound.http.MeasureDto
import org.javafreedom.kdiab.bff.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.bff.adapters.outbound.http.TreatmentDto
import org.javafreedom.kdiab.bff.adapters.outbound.http.TreatmentsClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineServiceTest {

    private val measuresClient = mockk<MeasuresClient>()
    private val treatmentsClient = mockk<TreatmentsClient>()
    private val service = TimelineService(measuresClient, treatmentsClient)

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-31T23:59:59Z"

    private fun measure(id: String, at: String = "2024-01-15T12:00:00Z") = MeasureDto(
        id = id, userId = userId, measuredAt = at, type = "CGM",
        data = buildJsonObject { put("sgv", 120.0) }, status = "ACTIVE",
    )

    private fun treatment(id: String, at: String = "2024-01-15T13:00:00Z") = TreatmentDto(
        id = id, userId = userId, treatedAt = at, type = "BOLUS",
        data = buildJsonObject { put("insulin", 4.0) },
    )

    @Test
    fun `getTimeline returns empty lists when no data`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth) } returns emptyList()
        val result = service.getTimeline(userId, from, to, auth)
        assertTrue(result.measures.isEmpty())
        assertTrue(result.treatments.isEmpty())
    }

    @Test
    fun `getTimeline filters measures outside timeframe`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth) } returns listOf(
            measure("inside", "2024-01-15T12:00:00Z"),
            measure("before", "2023-12-31T23:59:00Z"),
            measure("after", "2024-02-01T00:00:01Z"),
        )
        coEvery { treatmentsClient.getTreatments(userId, auth) } returns emptyList()
        val result = service.getTimeline(userId, from, to, auth)
        assertEquals(1, result.measures.size)
        assertEquals("inside", result.measures.first().id)
    }

    @Test
    fun `getTimeline filters treatments outside timeframe`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth) } returns listOf(
            treatment("inside", "2024-01-20T08:00:00Z"),
            treatment("before", "2023-12-01T00:00:00Z"),
        )
        val result = service.getTimeline(userId, from, to, auth)
        assertEquals(1, result.treatments.size)
        assertEquals("inside", result.treatments.first().id)
    }

    @Test
    fun `getTimeline includes boundary instants`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth) } returns listOf(
            measure("at-from", from),
            measure("at-to", to),
        )
        coEvery { treatmentsClient.getTreatments(userId, auth) } returns emptyList()
        val result = service.getTimeline(userId, from, to, auth)
        assertEquals(2, result.measures.size)
    }

    @Test
    fun `getTimeline maps measure fields correctly`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth) } returns listOf(measure("m-1"))
        coEvery { treatmentsClient.getTreatments(userId, auth) } returns emptyList()
        val result = service.getTimeline(userId, from, to, auth)
        val m = result.measures.first()
        assertEquals("m-1", m.id)
        assertEquals(userId, m.userId)
        assertEquals("CGM", m.type)
        assertEquals("ACTIVE", m.status)
    }

    @Test
    fun `getTimeline maps treatment fields correctly`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth) } returns listOf(
            TreatmentDto(
                id = "t-1", userId = userId, treatedAt = "2024-01-10T08:00:00Z",
                type = "BOLUS", notes = "breakfast",
                data = buildJsonObject { put("insulin", 6.0) },
            )
        )
        val result = service.getTimeline(userId, from, to, auth)
        val t = result.treatments.first()
        assertEquals("t-1", t.id)
        assertEquals("BOLUS", t.type)
        assertEquals("breakfast", t.notes)
    }
}

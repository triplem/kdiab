package org.javafreedom.kdiab.nightscout.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureStatus
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentType
import kotlin.test.Test
import kotlin.test.assertEquals

class NightscoutServiceTest {

    private val measuresClient = mockk<MeasuresClient>()
    private val treatmentsClient = mockk<TreatmentsClient>()
    private val service = NightscoutService(measuresClient, treatmentsClient)

    @Test
    fun `getEntries maps CGM measures to nightscout entries`() = runTest {
        val measureData = buildJsonObject {
            put("sgv", 140)
            put("trend", 4)
            put("direction", "Flat")
        }
        coEvery {
            measuresClient.getMeasures(any(), any(), any(), any(), any())
        } returns listOf(
            MeasureResponse(
                id = "m1",
                userId = "user1",
                measuredAt = "2024-01-01T00:00:00Z",
                createdAt = "2024-01-01T00:00:00Z",
                type = MeasureType.CGM,
                source = MeasureSource.NIGHTSCOUT,
                data = measureData,
                status = MeasureStatus.ACTIVE,
            )
        )

        val entries = service.getEntries("user1", "Bearer token", "corr")

        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("sgv", entry.type)
        assertEquals(140, entry.sgv)
        assertEquals(4, entry.trend)
        assertEquals("Flat", entry.direction)
    }

    @Test
    fun `getEntries ignores non-CGM measures`() = runTest {
        coEvery {
            measuresClient.getMeasures(any(), any(), any(), any(), any())
        } returns listOf(
            MeasureResponse(
                id = "m1",
                userId = "user1",
                measuredAt = "2024-01-01T00:00:00Z",
                createdAt = "2024-01-01T00:00:00Z",
                type = MeasureType.BLOOD_PRESSURE,
                source = MeasureSource.MANUAL,
                data = buildJsonObject { put("systolic", 120) },
                status = MeasureStatus.ACTIVE,
            )
        )

        val entries = service.getEntries("user1", "Bearer token", "corr")

        assertEquals(0, entries.size)
    }

    @Test
    fun `getTreatments maps bolus treatments to nightscout treatments`() = runTest {
        val treatmentData = buildJsonObject { put("insulin", 2.5) }
        coEvery {
            treatmentsClient.getTreatments(any(), any(), any(), any(), any())
        } returns listOf(
            TreatmentResponse(
                id = "t1",
                userId = "user1",
                treatedAt = "2024-01-01T08:00:00Z",
                createdAt = "2024-01-01T08:00:00Z",
                type = TreatmentType.BOLUS,
                data = treatmentData,
                status = TreatmentResponse.Status.ACTIVE,
                notes = null,
            )
        )

        val treatments = service.getTreatments("user1", "Bearer token", "corr")

        assertEquals(1, treatments.size)
        val treatment = treatments.first()
        assertEquals("Bolus", treatment.eventType)
        assertEquals(2.5, treatment.insulin)
    }

    @Test
    fun `getEntries respects count limit`() = runTest {
        val measures = (1..10).map { i ->
            MeasureResponse(
                id = "m$i",
                userId = "user1",
                measuredAt = "2024-01-01T${"%02d".format(i - 1)}:00:00Z",
                createdAt = "2024-01-01T${"%02d".format(i - 1)}:00:00Z",
                type = MeasureType.CGM,
                source = MeasureSource.NIGHTSCOUT,
                data = buildJsonObject { put("sgv", 100 + i) },
                status = MeasureStatus.ACTIVE,
            )
        }
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns measures

        val entries = service.getEntries("user1", "Bearer token", "corr", count = 3)

        assertEquals(3, entries.size)
    }
}

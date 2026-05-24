package org.javafreedom.kdiab.nightscout.application.service

import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.adapters.inbound.web.Ns3SearchParams
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureStatus
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.UpdateMeasureRequest
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NightscoutV3ServiceTest {

    private val measuresClient = mockk<MeasuresClient>()
    private val service = NightscoutV3Service(measuresClient)

    private val defaultParams = Ns3SearchParams(
        limit = 100,
        skip = 0,
        sortField = "date",
        sortDesc = true,
        fields = emptyList(),
        filters = emptyMap(),
    )

    private fun measureResponse(id: String, measuredAt: String, sgv: Int = 120) = MeasureResponse(
        id = id,
        userId = "user1",
        measuredAt = measuredAt,
        createdAt = measuredAt,
        type = MeasureType.CGM,
        source = MeasureSource.NIGHTSCOUT,
        data = buildJsonObject { put("sgv", sgv) },
        status = MeasureStatus.ACTIVE,
    )

    @Test
    fun `searchEntries returns sorted and limited entries`() = runTest {
        coEvery {
            measuresClient.getMeasures(any(), any(), any(), any(), any())
        } returns listOf(
            measureResponse("m1", "2024-01-01T00:00:00Z", 100),
            measureResponse("m2", "2024-01-01T01:00:00Z", 120),
            measureResponse("m3", "2024-01-01T02:00:00Z", 140),
        )

        val params = defaultParams.copy(limit = 2, sortDesc = true)
        val result = service.searchEntries("user1", "Bearer token", "corr", params, "mg/dL")

        assertEquals(2, result.size)
        // sortDesc=true: newest first
        assertEquals("m3", result[0].identifier)
        assertEquals("m2", result[1].identifier)
    }

    @Test
    fun `searchEntries sorts ascending when sortDesc is false`() = runTest {
        coEvery {
            measuresClient.getMeasures(any(), any(), any(), any(), any())
        } returns listOf(
            measureResponse("m1", "2024-01-01T02:00:00Z"),
            measureResponse("m2", "2024-01-01T00:00:00Z"),
        )

        val params = defaultParams.copy(sortDesc = false)
        val result = service.searchEntries("user1", "Bearer token", "corr", params, "mg/dL")

        assertEquals("m2", result[0].identifier)
        assertEquals("m1", result[1].identifier)
    }

    @Test
    fun `getEntry returns converted entry when found`() = runTest {
        coEvery {
            measuresClient.getMeasure("user1", "Bearer token", "corr", "m1")
        } returns measureResponse("m1", "2024-01-01T00:00:00Z", 150)

        val entry = service.getEntry("user1", "Bearer token", "corr", "m1", "mg/dL")

        assertEquals("m1", entry?.identifier)
        assertEquals(150.0, entry?.sgv)
    }

    @Test
    fun `getEntry returns null when not found`() = runTest {
        coEvery {
            measuresClient.getMeasure("user1", "Bearer token", "corr", "missing")
        } returns null

        val entry = service.getEntry("user1", "Bearer token", "corr", "missing", "mg/dL")

        assertNull(entry)
    }

    @Test
    fun `createEntry calls postMeasure and returns the input entry`() = runTest {
        coJustRun { measuresClient.postMeasure(any(), any(), any(), any()) }

        val inputEntry = Ns3Entry(
            identifier = "new-id",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 130.0,
        )

        val result = service.createEntry("user1", "Bearer token", "corr", inputEntry, "mg/dL")

        assertEquals(inputEntry, result)
        coVerify(exactly = 1) { measuresClient.postMeasure("user1", "Bearer token", "corr", any()) }
    }

    @Test
    fun `updateEntry calls updateMeasure and returns converted entry`() = runTest {
        val updatedResponse = measureResponse("m1", "2024-01-01T00:00:00Z", 180)
        coEvery {
            measuresClient.updateMeasure("user1", "Bearer token", "corr", "m1", any())
        } returns updatedResponse

        val entry = Ns3Entry(
            identifier = "m1",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 180.0,
        )

        val result = service.updateEntry("user1", "Bearer token", "corr", "m1", entry, "mg/dL")

        assertEquals("m1", result.identifier)
        assertEquals(180.0, result.sgv)
        coVerify(exactly = 1) {
            measuresClient.updateMeasure(
                "user1",
                "Bearer token",
                "corr",
                "m1",
                any<UpdateMeasureRequest>(),
            )
        }
    }

    @Test
    fun `deleteEntry with permanent false calls deleteMeasure with permanent false`() = runTest {
        coJustRun { measuresClient.deleteMeasure(any(), any(), any(), any(), any()) }

        service.deleteEntry("user1", "Bearer token", "corr", "m1", permanent = false)

        coVerify(exactly = 1) {
            measuresClient.deleteMeasure("user1", "Bearer token", "corr", "m1", permanent = false)
        }
    }

    @Test
    fun `deleteEntry with permanent true calls deleteMeasure with permanent true`() = runTest {
        coJustRun { measuresClient.deleteMeasure(any(), any(), any(), any(), any()) }

        service.deleteEntry("user1", "Bearer token", "corr", "m1", permanent = true)

        coVerify(exactly = 1) {
            measuresClient.deleteMeasure("user1", "Bearer token", "corr", "m1", permanent = true)
        }
    }
}

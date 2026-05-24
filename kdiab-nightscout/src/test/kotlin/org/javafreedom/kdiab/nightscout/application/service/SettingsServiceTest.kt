package org.javafreedom.kdiab.nightscout.application.service

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.CarbsClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SettingsServiceTest {

    private val measuresClient = mockk<MeasuresClient>()
    private val treatmentsClient = mockk<TreatmentsClient>()
    private val carbsClient = mockk<CarbsClient>()
    private val profilesClient = mockk<ProfilesClient>()

    private val service = NightscoutV3Service(
        measuresClient = measuresClient,
        treatmentsClient = treatmentsClient,
        carbsClient = carbsClient,
        profilesClient = profilesClient,
    )

    @Test
    fun `getSettings returns units from glucoseUnit when mg-dL`() = runTest {
        val settings = service.getSettings(
            userId = "user-1",
            authorization = "Bearer token",
            correlationId = "corr-1",
            glucoseUnit = "mg/dL",
        )

        assertEquals("mg/dL", settings.units)
        assertEquals("user-1", settings.identifier)
        assertEquals("UTC", settings.timeZone)
    }

    @Test
    fun `getSettings returns units from glucoseUnit when mmol`() = runTest {
        val settings = service.getSettings(
            userId = "user-2",
            authorization = "Bearer token",
            correlationId = "corr-2",
            glucoseUnit = "mmol/L",
        )

        assertEquals("mmol/L", settings.units)
        assertEquals("user-2", settings.identifier)
    }

    @Test
    fun `getSettings sets timeZone to UTC`() = runTest {
        val settings = service.getSettings(
            userId = "user-1",
            authorization = "Bearer token",
            correlationId = "corr-1",
            glucoseUnit = "mg/dL",
        )

        assertNotNull(settings)
        assertEquals("UTC", settings.timeZone)
    }
}

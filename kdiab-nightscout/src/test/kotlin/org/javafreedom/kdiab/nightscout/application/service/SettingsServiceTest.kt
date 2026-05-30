package org.javafreedom.kdiab.nightscout.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.CarbsClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.UserSettingsClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SettingsServiceTest {

    private val measuresClient = mockk<MeasuresClient>()
    private val treatmentsClient = mockk<TreatmentsClient>()
    private val carbsClient = mockk<CarbsClient>()
    private val profilesClient = mockk<ProfilesClient>()
    private val userSettingsClient = mockk<UserSettingsClient>()

    private val service = NightscoutV3Service(
        measuresClient = measuresClient,
        treatmentsClient = treatmentsClient,
        carbsClient = carbsClient,
        profilesClient = profilesClient,
        userSettingsClient = userSettingsClient,
    )

    @Test
    fun `getSettings returns units from userSettingsClient when mg-dL`() = runTest {
        coEvery { userSettingsClient.getGlucoseUnit(any()) } returns "mg/dL"

        val settings = service.getSettings(
            userId = "user-1",
            authorization = "Bearer token",
        )

        assertEquals("mg/dL", settings.units)
        assertEquals("user-1", settings.identifier)
        assertEquals("UTC", settings.timeZone)
    }

    @Test
    fun `getSettings returns units from userSettingsClient when mmol-L`() = runTest {
        coEvery { userSettingsClient.getGlucoseUnit(any()) } returns "mmol/L"

        val settings = service.getSettings(
            userId = "user-2",
            authorization = "Bearer token",
        )

        assertEquals("mmol/L", settings.units)
        assertEquals("user-2", settings.identifier)
    }

    @Test
    fun `getSettings sets timeZone to UTC`() = runTest {
        coEvery { userSettingsClient.getGlucoseUnit(any()) } returns "mg/dL"

        val settings = service.getSettings(
            userId = "user-1",
            authorization = "Bearer token",
        )

        assertNotNull(settings)
        assertEquals("UTC", settings.timeZone)
    }
}

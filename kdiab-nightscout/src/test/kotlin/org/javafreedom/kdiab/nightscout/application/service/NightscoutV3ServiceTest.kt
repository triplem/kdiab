package org.javafreedom.kdiab.nightscout.application.service

import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.CarbsClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.FoodEntryResponse
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.FoodEntryStatus
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.PagedFoodResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureStatus
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.UpdateMeasureRequest
import org.javafreedom.kdiab.nightscout.api.upstream.profiles.models.CreateProfileRequest
import org.javafreedom.kdiab.nightscout.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Food
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Profile
import org.javafreedom.kdiab.nightscout.domain.model.Ns3SearchParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NightscoutV3ServiceTest {

    private val measuresClient = mockk<MeasuresClient>()
    private val treatmentsClient = mockk<TreatmentsClient>()
    private val carbsClient = mockk<CarbsClient>()
    private val profilesClient = mockk<ProfilesClient>()
    private val service = NightscoutV3Service(measuresClient, treatmentsClient, carbsClient, profilesClient)

    private val defaultParams = Ns3SearchParams(
        limit = 100,
        skip = 0,
        sortField = "date",
        sortDesc = true,
        fields = emptyList(),
        filters = emptyMap<String, List<Pair<String, String>>>(),
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
    fun `createEntry calls postMeasure and returns entry with server-assigned id`() = runTest {
        val serverResponse = measureResponse("server-assigned-id", "2024-01-01T00:00:00Z", 130)
        coEvery { measuresClient.postMeasure(any(), any(), any(), any()) } returns serverResponse

        val inputEntry = Ns3Entry(
            identifier = "client-temp-id",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 130.0,
        )

        val result = service.createEntry("user1", "Bearer token", "corr", inputEntry, "mg/dL")

        assertEquals("server-assigned-id", result.identifier)
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

    // ---- food methods ----

    private fun foodEntryResponse(id: String = "food-1") = FoodEntryResponse(
        id = id,
        userId = "user1",
        name = "Apple",
        portionGrams = java.math.BigDecimal("150"),
        carbsPer100g = java.math.BigDecimal("14"),
        carbsForPortion = java.math.BigDecimal("21"),
        status = FoodEntryStatus.ACTIVE,
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-01-01T00:00:00Z",
    )

    private fun pagedFoodResponse(vararg entries: FoodEntryResponse) = PagedFoodResponse(
        items = entries.toList(),
        page = 0,
        propertySize = 200,
        totalCount = entries.size,
    )

    @Test
    fun `searchFood returns mapped food items`() = runTest {
        coEvery { carbsClient.listFood(any(), any(), any(), any(), any()) } returns pagedFoodResponse(foodEntryResponse())
        val params = defaultParams.copy(limit = 10, skip = 0)

        val result = service.searchFood("user1", "Bearer token", "corr", params)

        assertEquals(1, result.size)
        assertEquals("food-1", result[0].identifier)
        assertEquals("Apple", result[0].name)
        assertEquals(21.0, result[0].carbs)
    }

    @Test
    fun `searchFood applies skip and limit`() = runTest {
        val items = (1..5).map { foodEntryResponse("food-$it") }.toTypedArray()
        coEvery { carbsClient.listFood(any(), any(), any(), any(), any()) } returns pagedFoodResponse(*items)
        val params = defaultParams.copy(limit = 2, skip = 2)

        val result = service.searchFood("user1", "Bearer token", "corr", params)

        assertEquals(2, result.size)
        assertEquals("food-3", result[0].identifier)
        assertEquals("food-4", result[1].identifier)
    }

    @Test
    fun `getFood returns mapped food when found`() = runTest {
        coEvery { carbsClient.getFood("user1", "Bearer token", "corr", "food-1") } returns foodEntryResponse()

        val result = service.getFood("user1", "Bearer token", "corr", "food-1")

        assertNotNull(result)
        assertEquals("food-1", result.identifier)
    }

    @Test
    fun `getFood returns null when not found`() = runTest {
        coEvery { carbsClient.getFood("user1", "Bearer token", "corr", "missing") } returns null

        val result = service.getFood("user1", "Bearer token", "corr", "missing")

        assertNull(result)
    }

    @Test
    fun `createFood calls carbsClient and returns created food`() = runTest {
        coEvery { carbsClient.createFood(any(), any(), any(), any()) } returns foodEntryResponse("server-id")

        val food = Ns3Food(identifier = "temp", name = "Apple", carbs = 21.0, portionSize = 150.0)
        val result = service.createFood("user1", "Bearer token", "corr", food)

        assertEquals("server-id", result.identifier)
        coVerify(exactly = 1) { carbsClient.createFood("user1", "Bearer token", "corr", any()) }
    }

    @Test
    fun `updateFood calls carbsClient and returns updated food`() = runTest {
        coEvery { carbsClient.updateFood(any(), any(), any(), "food-1", any()) } returns foodEntryResponse()

        val food = Ns3Food(identifier = "food-1", name = "Apple", carbs = 21.0, portionSize = 150.0)
        val result = service.updateFood("user1", "Bearer token", "corr", "food-1", food)

        assertEquals("food-1", result.identifier)
        coVerify(exactly = 1) { carbsClient.updateFood("user1", "Bearer token", "corr", "food-1", any()) }
    }

    @Test
    fun `deleteFood calls carbsClient with permanent false`() = runTest {
        coJustRun { carbsClient.deleteFood(any(), any(), any(), any(), any()) }

        service.deleteFood("user1", "Bearer token", "corr", "food-1", permanent = false)

        coVerify(exactly = 1) {
            carbsClient.deleteFood("user1", "Bearer token", "corr", "food-1", permanent = false)
        }
    }

    @Test
    fun `deleteFood with permanent true calls carbsClient with permanent true`() = runTest {
        coJustRun { carbsClient.deleteFood(any(), any(), any(), any(), any()) }

        service.deleteFood("user1", "Bearer token", "corr", "food-1", permanent = true)

        coVerify(exactly = 1) {
            carbsClient.deleteFood("user1", "Bearer token", "corr", "food-1", permanent = true)
        }
    }

    // ─── Profile service methods ───────────────────────────────────────────────

    private fun upstreamProfile(
        id: String = "p1",
        name: String = "Test Profile",
        durationOfAction: Int = 240,
        status: Profile.Status = Profile.Status.ACTIVE,
    ) = Profile(
        id = id,
        userId = "user1",
        name = name,
        insulinType = "Novorapid",
        durationOfAction = durationOfAction,
        status = status,
        createdAt = "2024-01-01T00:00:00Z",
    )

    @Test
    fun `searchProfiles returns mapped profiles sorted by srvModified desc`() = runTest {
        coEvery { profilesClient.listProfiles(any(), any(), any(), any()) } returns listOf(
            upstreamProfile(id = "p1", name = "Profile A"),
            upstreamProfile(id = "p2", name = "Profile B"),
        )

        val params = defaultParams.copy(limit = 10, skip = 0)
        val result = service.searchProfiles("user1", "Bearer token", "corr", params)

        assertEquals(2, result.size)
        assertEquals("p1", result[0].identifier)
        assertEquals("p2", result[1].identifier)
    }

    @Test
    fun `searchProfiles applies skip and limit`() = runTest {
        coEvery { profilesClient.listProfiles(any(), any(), any(), any()) } returns listOf(
            upstreamProfile(id = "p1"),
            upstreamProfile(id = "p2"),
            upstreamProfile(id = "p3"),
        )

        val params = defaultParams.copy(limit = 1, skip = 1)
        val result = service.searchProfiles("user1", "Bearer token", "corr", params)

        assertEquals(1, result.size)
    }

    @Test
    fun `getProfile returns mapped profile when found`() = runTest {
        coEvery { profilesClient.getProfile("user1", "Bearer token", "corr", "p1") } returns upstreamProfile(id = "p1")

        val result = service.getProfile("user1", "Bearer token", "corr", "p1")

        assertEquals("p1", result?.identifier)
        assertEquals(4.0, result?.dia)
    }

    @Test
    fun `getProfile returns null when not found`() = runTest {
        coEvery { profilesClient.getProfile(any(), any(), any(), "missing") } returns null

        val result = service.getProfile("user1", "Bearer token", "corr", "missing")

        assertNull(result)
    }

    @Test
    fun `createProfile calls createProfile on client and returns mapped result`() = runTest {
        val created = upstreamProfile(id = "server-id", name = "New Profile")
        coEvery { profilesClient.createProfile(any(), any(), any(), any()) } returns created

        val input = Ns3Profile(
            identifier = "temp",
            defaultProfile = "New Profile",
            startDate = "2024-01-01T00:00:00Z",
            units = "mg/dl",
            dia = 4.0,
            basalSegments = emptyList(),
            carbratio = emptyList(),
            sens = emptyList(),
        )

        val result = service.createProfile("user1", "Bearer token", "corr", input)

        assertEquals("server-id", result.identifier)
        coVerify(exactly = 1) { profilesClient.createProfile("user1", "Bearer token", "corr", any<CreateProfileRequest>()) }
    }

    @Test
    fun `updateProfile fetches existing then updates and returns new profile`() = runTest {
        val existing = upstreamProfile(id = "p1", name = "Old Name")
        val updated = upstreamProfile(id = "p1-new", name = "New Name")
        coEvery { profilesClient.getProfile("user1", "Bearer token", "corr", "p1") } returns existing
        coEvery { profilesClient.updateProfile("user1", "Bearer token", "corr", "p1", any()) } returns updated

        val input = Ns3Profile(
            identifier = "p1",
            defaultProfile = "New Name",
            startDate = "2024-01-01T00:00:00Z",
            units = "mg/dl",
            dia = 4.0,
            basalSegments = emptyList(),
            carbratio = emptyList(),
            sens = emptyList(),
        )

        val result = service.updateProfile("user1", "Bearer token", "corr", "p1", input)

        assertEquals("p1-new", result.identifier)
        coVerify(exactly = 1) { profilesClient.updateProfile("user1", "Bearer token", "corr", "p1", any()) }
    }

    @Test
    fun `deleteProfile calls archiveProfile when permanent is false`() = runTest {
        coJustRun { profilesClient.archiveProfile(any(), any(), any(), any()) }

        service.deleteProfile("user1", "Bearer token", "corr", "p1", permanent = false)

        coVerify(exactly = 1) { profilesClient.archiveProfile("user1", "Bearer token", "corr", "p1") }
    }

    @Test
    fun `deleteProfile throws when permanent is true`() = runTest {
        val result = runCatching {
            service.deleteProfile("user1", "Bearer token", "corr", "p1", permanent = true)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Permanent deletion") == true)
    }
}

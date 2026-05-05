@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.treatments.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType
import org.javafreedom.kdiab.treatments.domain.repository.TreatmentRepository

class TreatmentServiceTest {

    private val repo = mockk<TreatmentRepository>()
    private val service = TreatmentService(repo)

    private val userId      = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val treatmentId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    private fun testTreatment() = Treatment(
        id = treatmentId,
        userId = userId,
        treatedAt = Instant.parse("2024-01-01T10:00:00Z"),
        createdAt  = Instant.parse("2024-01-01T10:00:00Z"),
        type = TreatmentType.BOLUS,
        data = buildJsonObject { put("insulin", 2.5) },
    )

    @Test
    fun `addTreatment saves and returns the treatment`() = runTest {
        val treatment = testTreatment()
        coEvery { repo.save(treatment) } returns treatment
        val result = service.addTreatment(treatment)
        assertEquals(treatment, result)
        coVerify(exactly = 1) { repo.save(treatment) }
    }

    @Test
    fun `getTreatments returns list from repository`() = runTest {
        val treatments = listOf(testTreatment())
        coEvery { repo.findByUserId(userId, null, null) } returns treatments
        assertEquals(treatments, service.getTreatments(userId))
    }

    @Test
    fun `getTreatments returns empty list when no treatments found`() = runTest {
        coEvery { repo.findByUserId(userId, null, null) } returns emptyList()
        assertEquals(emptyList(), service.getTreatments(userId))
    }

    @Test
    fun `getTreatments passes from and to to repository`() = runTest {
        val from = Instant.parse("2024-01-01T00:00:00Z")
        val to = Instant.parse("2024-01-31T23:59:59Z")
        val treatments = listOf(testTreatment())
        coEvery { repo.findByUserId(userId, from, to) } returns treatments
        assertEquals(treatments, service.getTreatments(userId, from, to))
    }

    @Test
    fun `getTreatmentsByType returns filtered list from repository`() = runTest {
        val treatments = listOf(testTreatment())
        coEvery { repo.findByUserIdAndType(userId, TreatmentType.BOLUS) } returns treatments
        assertEquals(treatments, service.getTreatmentsByType(userId, TreatmentType.BOLUS))
    }

    @Test
    fun `deleteTreatments delegates to repository`() = runTest {
        coEvery { repo.deleteAll(listOf(treatmentId), userId) } just runs
        service.deleteTreatments(listOf(treatmentId), userId)
        coVerify(exactly = 1) { repo.deleteAll(listOf(treatmentId), userId) }
    }

    @Test
    fun `deleteTreatments throws ResourceNotFoundException when ids are empty`() = runTest {
        assertFailsWith<ResourceNotFoundException> {
            service.deleteTreatments(emptyList(), userId)
        }
        coVerify(exactly = 0) { repo.deleteAll(any(), any()) }
    }
}

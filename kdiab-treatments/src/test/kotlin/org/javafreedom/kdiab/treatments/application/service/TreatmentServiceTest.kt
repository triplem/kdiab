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
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.treatments.domain.model.PagedTreatments
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentStatus
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
    fun `getTreatments returns paged result from repository`() = runTest {
        val treatments = listOf(testTreatment())
        coEvery { repo.findByUserId(userId, null, null, TreatmentStatus.ACTIVE, 0, 50) } returns treatments
        coEvery { repo.countByUserId(userId, null, null, TreatmentStatus.ACTIVE) } returns 1L
        val result = service.getTreatments(userId)
        assertEquals(PagedTreatments(treatments, 0, 50, 1L), result)
    }

    @Test
    fun `getTreatments returns empty paged result when no treatments found`() = runTest {
        coEvery { repo.findByUserId(userId, null, null, TreatmentStatus.ACTIVE, 0, 50) } returns emptyList()
        coEvery { repo.countByUserId(userId, null, null, TreatmentStatus.ACTIVE) } returns 0L
        val result = service.getTreatments(userId)
        assertEquals(PagedTreatments(emptyList(), 0, 50, 0L), result)
    }

    @Test
    fun `getTreatments passes from and to to repository`() = runTest {
        val from = Instant.parse("2024-01-01T00:00:00Z")
        val to = Instant.parse("2024-01-31T23:59:59Z")
        val treatments = listOf(testTreatment())
        coEvery { repo.findByUserId(userId, from, to, TreatmentStatus.ACTIVE, 0, 50) } returns treatments
        coEvery { repo.countByUserId(userId, from, to, TreatmentStatus.ACTIVE) } returns 1L
        val result = service.getTreatments(userId, from, to)
        assertEquals(PagedTreatments(treatments, 0, 50, 1L), result)
    }

    @Test
    fun `getTreatments passes status to repository`() = runTest {
        val treatments = listOf(testTreatment())
        coEvery { repo.findByUserId(userId, null, null, TreatmentStatus.ARCHIVED, 0, 50) } returns treatments
        coEvery { repo.countByUserId(userId, null, null, TreatmentStatus.ARCHIVED) } returns 1L
        val result = service.getTreatments(userId, status = TreatmentStatus.ARCHIVED)
        assertEquals(PagedTreatments(treatments, 0, 50, 1L), result)
    }

    @Test
    fun `getTreatments with pagination - returns correct page and size`() = runTest {
        val allTreatments = (1..10).map { testTreatment() }
        val firstPage = allTreatments.take(5)
        coEvery { repo.findByUserId(userId, null, null, TreatmentStatus.ACTIVE, 0, 5) } returns firstPage
        coEvery { repo.countByUserId(userId, null, null, TreatmentStatus.ACTIVE) } returns 10L
        val result = service.getTreatments(userId, page = 0, size = 5)
        assertEquals(5, result.items.size)
        assertEquals(10L, result.totalCount)
        assertEquals(0, result.page)
        assertEquals(5, result.size)
    }

    @Test
    fun `getTreatments with page beyond total returns empty list`() = runTest {
        coEvery { repo.findByUserId(userId, null, null, TreatmentStatus.ACTIVE, 1, 50) } returns emptyList()
        coEvery { repo.countByUserId(userId, null, null, TreatmentStatus.ACTIVE) } returns 5L

        val result = service.getTreatments(userId, page = 1, size = 50)

        assertEquals(PagedTreatments(emptyList(), 1, 50, 5L), result)
    }

    @Test
    fun `getTreatments with very large page number returns empty items`() = runTest {
        coEvery { repo.findByUserId(userId, null, null, TreatmentStatus.ACTIVE, 999, 50) } returns emptyList()
        coEvery { repo.countByUserId(userId, null, null, TreatmentStatus.ACTIVE) } returns 5L

        val result = service.getTreatments(userId, page = 999, size = 50)

        assertEquals(emptyList(), result.items)
    }

    @Test
    fun `getTreatmentsByType returns filtered list from repository`() = runTest {
        val treatments = listOf(testTreatment())
        coEvery { repo.findByUserIdAndType(userId, TreatmentType.BOLUS, null, null, TreatmentStatus.ACTIVE) } returns treatments
        assertEquals(treatments, service.getTreatmentsByType(userId, TreatmentType.BOLUS))
    }

    @Test
    fun `getTreatmentsByType passes from and to to repository`() = runTest {
        val from = Instant.parse("2024-01-01T00:00:00Z")
        val to = Instant.parse("2024-01-31T23:59:59Z")
        val treatments = listOf(testTreatment())
        coEvery { repo.findByUserIdAndType(userId, TreatmentType.BOLUS, from, to, TreatmentStatus.ACTIVE) } returns treatments
        assertEquals(treatments, service.getTreatmentsByType(userId, TreatmentType.BOLUS, from, to))
    }

    @Test
    fun `archiveTreatments delegates to repository`() = runTest {
        coEvery { repo.archiveAll(listOf(treatmentId), userId) } just runs
        service.archiveTreatments(listOf(treatmentId), userId)
        coVerify(exactly = 1) { repo.archiveAll(listOf(treatmentId), userId) }
    }

    @Test
    fun `archiveTreatments throws ResourceNotFoundException when ids are empty`() = runTest {
        assertFailsWith<ResourceNotFoundException> {
            service.archiveTreatments(emptyList(), userId)
        }
        coVerify(exactly = 0) { repo.archiveAll(any(), any()) }
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

    @Test
    fun `getDeviceAge returns timestamps for all three device types`() = runTest {
        val catheter  = Instant.parse("2026-05-14T10:00:00Z")
        val reservoir = Instant.parse("2026-05-13T08:00:00Z")
        val sensor    = Instant.parse("2026-05-12T18:00:00Z")
        coEvery {
            repo.findLatestTimestampsByTypes(
                userId,
                setOf(TreatmentType.SITE_CHANGE, TreatmentType.INSULIN_CHANGE, TreatmentType.SENSOR_INSERT),
            )
        } returns mapOf(
            TreatmentType.SITE_CHANGE    to catheter,
            TreatmentType.INSULIN_CHANGE to reservoir,
            TreatmentType.SENSOR_INSERT  to sensor,
        )

        val (c, r, s) = service.getDeviceAge(userId)

        assertEquals(catheter, c)
        assertEquals(reservoir, r)
        assertEquals(sensor, s)
    }

    @Test
    fun `getDeviceAge returns triple of nulls when no device treatments exist`() = runTest {
        coEvery {
            repo.findLatestTimestampsByTypes(
                userId,
                setOf(TreatmentType.SITE_CHANGE, TreatmentType.INSULIN_CHANGE, TreatmentType.SENSOR_INSERT),
            )
        } returns emptyMap()

        val (c, r, s) = service.getDeviceAge(userId)

        assertEquals(null, c)
        assertEquals(null, r)
        assertEquals(null, s)
    }

    @Test
    fun `getDeviceAge returns partial nulls when only some device treatments exist`() = runTest {
        val catheter = Instant.parse("2026-05-14T10:00:00Z")
        coEvery {
            repo.findLatestTimestampsByTypes(
                userId,
                setOf(TreatmentType.SITE_CHANGE, TreatmentType.INSULIN_CHANGE, TreatmentType.SENSOR_INSERT),
            )
        } returns mapOf(TreatmentType.SITE_CHANGE to catheter)

        val (c, r, s) = service.getDeviceAge(userId)

        assertEquals(catheter, c)
        assertEquals(null, r)
        assertEquals(null, s)
    }
}

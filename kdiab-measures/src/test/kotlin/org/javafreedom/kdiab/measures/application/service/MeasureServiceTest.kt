@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.application.service

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
import org.javafreedom.kdiab.measures.domain.model.Measure
import org.javafreedom.kdiab.measures.domain.model.MeasureSource
import org.javafreedom.kdiab.measures.domain.model.MeasureStatus
import org.javafreedom.kdiab.measures.domain.model.MeasureType
import org.javafreedom.kdiab.measures.domain.repository.MeasureRepository

class MeasureServiceTest {

    private val repo = mockk<MeasureRepository>()
    private val service = MeasureService(repo)

    private val userId    = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val measureId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    private fun testMeasure() = Measure(
        id = measureId,
        userId = userId,
        measuredAt = Instant.parse("2024-01-01T10:00:00Z"),
        createdAt  = Instant.parse("2024-01-01T10:00:00Z"),
        type   = MeasureType.BGM,
        source = MeasureSource.MANUAL,
        data   = buildJsonObject { put("mbg", 120) },
        status = MeasureStatus.ACTIVE
    )

    @Test
    fun `addMeasure saves and returns the measure`() = runTest {
        val measure = testMeasure()
        coEvery { repo.save(measure) } returns measure
        val result = service.addMeasure(measure)
        assertEquals(measure, result)
        coVerify(exactly = 1) { repo.save(measure) }
    }

    @Test
    fun `getMeasures returns paged result from repository`() = runTest {
        val measures = listOf(testMeasure())
        coEvery { repo.findByUserId(userId, 0, 50, null, null) } returns measures
        coEvery { repo.countByUserId(userId, null, null) } returns 1L
        val result = service.getMeasures(userId, 0, 50)
        assertEquals(measures, result.items)
        assertEquals(1L, result.totalCount)
    }

    @Test
    fun `getMeasures returns empty paged result when no measures found`() = runTest {
        coEvery { repo.findByUserId(userId, 0, 50, null, null) } returns emptyList()
        coEvery { repo.countByUserId(userId, null, null) } returns 0L
        val result = service.getMeasures(userId, 0, 50)
        assertEquals(emptyList(), result.items)
        assertEquals(0L, result.totalCount)
    }

    @Test
    fun `getMeasures passes from and to to repository`() = runTest {
        val from = Instant.parse("2024-01-01T00:00:00Z")
        val to = Instant.parse("2024-01-31T23:59:59Z")
        val measures = listOf(testMeasure())
        coEvery { repo.findByUserId(userId, 0, 50, from, to) } returns measures
        coEvery { repo.countByUserId(userId, from, to) } returns 1L
        val result = service.getMeasures(userId, 0, 50, from, to)
        assertEquals(measures, result.items)
        assertEquals(1L, result.totalCount)
    }

    @Test
    fun `getMeasures with page beyond total returns empty list`() = runTest {
        coEvery { repo.findByUserId(userId, 1, 50, null, null) } returns emptyList()
        coEvery { repo.countByUserId(userId, null, null) } returns 5L

        val result = service.getMeasures(userId, 1, 50)

        assertEquals(emptyList(), result.items)
        assertEquals(5L, result.totalCount)
    }

    @Test
    fun `getMeasures with very large page number returns empty items`() = runTest {
        coEvery { repo.findByUserId(userId, 999, 50, null, null) } returns emptyList()
        coEvery { repo.countByUserId(userId, null, null) } returns 5L

        val result = service.getMeasures(userId, 999, 50)

        assertEquals(emptyList(), result.items)
    }

    @Test
    fun `archiveMeasures delegates to repository`() = runTest {
        coEvery { repo.archive(listOf(measureId), userId) } just runs
        service.archiveMeasures(listOf(measureId), userId)
        coVerify(exactly = 1) { repo.archive(listOf(measureId), userId) }
    }

    @Test
    fun `archiveMeasures throws ResourceNotFoundException when ids are empty`() = runTest {
        assertFailsWith<ResourceNotFoundException> {
            service.archiveMeasures(emptyList(), userId)
        }
        coVerify(exactly = 0) { repo.archive(any(), any()) }
    }

    @Test
    fun `deleteMeasures delegates to repository`() = runTest {
        coEvery { repo.deleteAll(listOf(measureId), userId) } just runs
        service.deleteMeasures(listOf(measureId), userId)
        coVerify(exactly = 1) { repo.deleteAll(listOf(measureId), userId) }
    }

    @Test
    fun `deleteMeasures throws ResourceNotFoundException when ids are empty`() = runTest {
        assertFailsWith<ResourceNotFoundException> {
            service.deleteMeasures(emptyList(), userId)
        }
        coVerify(exactly = 0) { repo.deleteAll(any(), any()) }
    }

    @Test
    fun `updateMeasure delegates to repository and returns updated measure`() = runTest {
        val measuredAt = Instant.parse("2024-06-01T08:00:00Z")
        val data = buildJsonObject { put("mbg", 95) }
        val updated = testMeasure().copy(measuredAt = measuredAt, data = data)
        coEvery { repo.update(measureId, userId, measuredAt, data) } returns updated
        val result = service.updateMeasure(measureId, userId, measuredAt, data)
        assertEquals(updated, result)
        coVerify(exactly = 1) { repo.update(measureId, userId, measuredAt, data) }
    }

    @Test
    fun `unarchiveMeasures delegates to repository`() = runTest {
        coEvery { repo.unarchive(listOf(measureId), userId) } just runs
        service.unarchiveMeasures(listOf(measureId), userId)
        coVerify(exactly = 1) { repo.unarchive(listOf(measureId), userId) }
    }

    @Test
    fun `unarchiveMeasures throws ResourceNotFoundException when ids are empty`() = runTest {
        assertFailsWith<ResourceNotFoundException> {
            service.unarchiveMeasures(emptyList(), userId)
        }
        coVerify(exactly = 0) { repo.unarchive(any(), any()) }
    }

    @Test
    fun `getMeasures passes status filter to repository`() = runTest {
        val measures = listOf(testMeasure().copy(status = MeasureStatus.ARCHIVED))
        coEvery { repo.findByUserId(userId, 0, 50, null, null, MeasureStatus.ARCHIVED) } returns measures
        coEvery { repo.countByUserId(userId, null, null, MeasureStatus.ARCHIVED) } returns 1L
        val result = service.getMeasures(userId, 0, 50, status = MeasureStatus.ARCHIVED)
        assertEquals(measures, result.items)
        assertEquals(1L, result.totalCount)
    }
}

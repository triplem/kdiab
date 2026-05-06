@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.measures.domain.model.Measure
import org.javafreedom.kdiab.measures.domain.model.MeasureSource
import org.javafreedom.kdiab.measures.domain.model.MeasureStatus
import org.javafreedom.kdiab.measures.domain.model.MeasureType
import org.jetbrains.exposed.v1.jdbc.Database

class ExposedMeasureRepositoryTest {

    companion object {
        val db: Database = LiquibaseTestHelper.setup("measures_repo_test")
    }

    private val repository = ExposedMeasureRepository()

    @BeforeTest
    fun setUp() {
        LiquibaseTestHelper.cleanData(db)
    }

    private fun createMeasure(
        userId: Uuid = Uuid.random(),
        measuredAt: Instant = Instant.parse("2024-01-15T10:00:00Z"),
        type: MeasureType = MeasureType.CGM,
        source: MeasureSource = MeasureSource.NIGHTSCOUT,
    ): Measure = Measure(
        id = Uuid.random(),
        userId = userId,
        measuredAt = measuredAt,
        createdAt = Instant.parse("2024-01-15T10:00:00Z"),
        type = type,
        source = source,
        data = buildJsonObject { put("sgv", 120) },
        status = MeasureStatus.ACTIVE,
    )

    @Test
    fun `save - inserts and returns measure with all fields matching input`() = runBlocking {
        val userId = Uuid.random()
        val measure = createMeasure(userId = userId)

        val saved = repository.save(measure)

        assertEquals(measure.id, saved.id)
        assertEquals(measure.userId, saved.userId)
        assertEquals(measure.measuredAt, saved.measuredAt)
        assertEquals(measure.type, saved.type)
        assertEquals(measure.source, saved.source)
        assertEquals(measure.status, saved.status)
    }

    @Test
    fun `findByUserId - returns measures for correct user only`() = runBlocking {
        val userA = Uuid.random()
        val userB = Uuid.random()

        repository.save(createMeasure(userId = userA))
        repository.save(createMeasure(userId = userA))
        repository.save(createMeasure(userId = userB))

        val results = repository.findByUserId(userA, page = 0, size = 10, from = null, to = null)

        assertEquals(2, results.size)
        assertTrue(results.all { it.userId == userA })
    }

    @Test
    fun `findByUserId - pagination returns correct pages`() = runBlocking {
        val userId = Uuid.random()

        repeat(5) {
            repository.save(
                createMeasure(
                    userId = userId,
                    measuredAt = Instant.parse("2024-01-${10 + it}T10:00:00Z"),
                )
            )
        }

        val page0 = repository.findByUserId(userId, page = 0, size = 3, from = null, to = null)
        val page1 = repository.findByUserId(userId, page = 1, size = 3, from = null, to = null)

        assertEquals(3, page0.size)
        assertEquals(2, page1.size)
    }

    @Test
    fun `findByUserId - date range filtering returns only measures in range`() = runBlocking {
        val userId = Uuid.random()
        val t = Instant.parse("2024-01-15T12:00:00Z")

        // t - 2 days: outside range
        repository.save(createMeasure(userId = userId, measuredAt = t.minus(2.days)))
        // t - 1 day: inside range (between t-36h and t-12h)
        repository.save(createMeasure(userId = userId, measuredAt = t.minus(1.days)))
        // t + 1 day: outside range
        repository.save(createMeasure(userId = userId, measuredAt = t.plus(1.days)))

        val from = t.minus(36.hours)
        val to = t.minus(12.hours)

        val results = repository.findByUserId(userId, page = 0, size = 10, from = from, to = to)

        assertEquals(1, results.size)
        assertEquals(t.minus(1.days), results[0].measuredAt)
    }

    @Test
    fun `findByUserIdAndType - filters by type`() = runBlocking {
        val userId = Uuid.random()

        repository.save(createMeasure(userId = userId, type = MeasureType.CGM))
        repository.save(createMeasure(userId = userId, type = MeasureType.BGM))

        val results = repository.findByUserIdAndType(userId, MeasureType.CGM)

        assertEquals(1, results.size)
        assertEquals(MeasureType.CGM, results[0].type)
    }

    @Test
    fun `archive - marks measure status as ARCHIVED and excludes from findByUserId`() = runBlocking {
        val userId = Uuid.random()
        val measure = repository.save(createMeasure(userId = userId))

        repository.archive(listOf(measure.id), userId)

        val results = repository.findByUserId(userId, page = 0, size = 10, from = null, to = null)
        assertEquals(0, results.size)
    }

    @Test
    fun `deleteAll - removes records from repository`() = runBlocking {
        val userId = Uuid.random()
        val measure1 = repository.save(createMeasure(userId = userId))
        val measure2 = repository.save(createMeasure(userId = userId))

        repository.deleteAll(listOf(measure1.id), userId)

        val results = repository.findByUserId(userId, page = 0, size = 10, from = null, to = null)
        assertEquals(1, results.size)
        assertEquals(measure2.id, results[0].id)
    }
}

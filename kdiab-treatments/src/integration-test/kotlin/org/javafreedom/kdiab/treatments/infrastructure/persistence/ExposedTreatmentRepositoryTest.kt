@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Repository-level integration tests for [ExposedTreatmentRepository].
 *
 * Schema is bootstrapped via Liquibase (see [LiquibaseTestHelper]) against an H2 in-memory
 * database. Data is cleared before each test so that row-count assertions are not affected
 * by previously inserted rows.
 */
class ExposedTreatmentRepositoryTest {

    private lateinit var repository: ExposedTreatmentRepository

    companion object {
        private val db: Database = LiquibaseTestHelper.setup("treatments_repo")
    }

    @BeforeTest
    fun setup() {
        LiquibaseTestHelper.cleanData(db)
        repository = ExposedTreatmentRepository()
    }

    private fun testTreatment(
        userId: Uuid = Uuid.parse("11111111-1111-1111-1111-111111111111"),
        type: TreatmentType = TreatmentType.BOLUS,
        treatedAt: Instant = Instant.parse("2024-01-15T10:00:00Z"),
    ) = Treatment(
        id = Uuid.random(),
        userId = userId,
        treatedAt = treatedAt,
        createdAt = Instant.parse("2024-01-15T10:00:00Z"),
        type = type,
        data = buildJsonObject { put("insulin", 2.5) },
    )

    @Test
    fun `save - returns saved treatment with correct fields`() = runBlocking {
        val input = testTreatment()
        val saved = repository.save(input)
        assertEquals(input.id, saved.id)
        assertEquals(input.userId, saved.userId)
        assertEquals(input.type, saved.type)
    }

    @Test
    fun `findByUserId - returns only treatments for that user, ordered by treatedAt DESC`() = runBlocking {
        val userA = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val userB = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val t1 = testTreatment(userA, treatedAt = Instant.parse("2024-01-15T10:00:00Z"))
        val t2 = testTreatment(userA, treatedAt = Instant.parse("2024-01-16T10:00:00Z"))
        val t3 = testTreatment(userB)
        repository.save(t1); repository.save(t2); repository.save(t3)
        val results = repository.findByUserId(userA, page = 0, size = 50)
        assertEquals(2, results.size)
        // ordered DESC: t2 first
        assertEquals(t2.id, results[0].id)
        assertEquals(t1.id, results[1].id)
    }

    @Test
    fun `findByUserId - applies pagination correctly`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val treatments = (1..10).map { i ->
            testTreatment(userId, treatedAt = Instant.parse("2024-01-${10 + i}T10:00:00Z"))
        }
        treatments.forEach { repository.save(it) }

        val page0 = repository.findByUserId(userId, page = 0, size = 5)
        val page1 = repository.findByUserId(userId, page = 1, size = 5)
        assertEquals(5, page0.size)
        assertEquals(5, page1.size)
        // pages should be disjoint
        val page0Ids = page0.map { it.id }.toSet()
        val page1Ids = page1.map { it.id }.toSet()
        assertEquals(0, (page0Ids intersect page1Ids).size)
    }

    @Test
    fun `countByUserId - returns correct total count`() = runBlocking {
        val userA = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val userB = Uuid.parse("22222222-2222-2222-2222-222222222222")
        repository.save(testTreatment(userA))
        repository.save(testTreatment(userA))
        repository.save(testTreatment(userB))

        assertEquals(2L, repository.countByUserId(userA))
        assertEquals(1L, repository.countByUserId(userB))
    }

    @Test
    fun `findByUserIdAndType - returns only matching type`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        repository.save(testTreatment(userId, TreatmentType.BOLUS))
        repository.save(testTreatment(userId, TreatmentType.CARBS))
        val bolus = repository.findByUserIdAndType(userId, TreatmentType.BOLUS)
        val carbs = repository.findByUserIdAndType(userId, TreatmentType.CARBS)
        assertEquals(1, bolus.size)
        assertEquals(1, carbs.size)
        assertEquals(TreatmentType.BOLUS, bolus[0].type)
        assertEquals(TreatmentType.CARBS, carbs[0].type)
    }

    @Test
    fun `findByUserIdAndType - applies date range filtering`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val early = testTreatment(userId, TreatmentType.BOLUS, Instant.parse("2024-01-05T10:00:00Z"))
        val mid   = testTreatment(userId, TreatmentType.BOLUS, Instant.parse("2024-01-15T10:00:00Z"))
        val late  = testTreatment(userId, TreatmentType.BOLUS, Instant.parse("2024-01-25T10:00:00Z"))
        repository.save(early); repository.save(mid); repository.save(late)

        val from = Instant.parse("2024-01-10T00:00:00Z")
        val to   = Instant.parse("2024-01-20T00:00:00Z")
        val results = repository.findByUserIdAndType(userId, TreatmentType.BOLUS, from, to)
        assertEquals(1, results.size)
        assertEquals(mid.id, results[0].id)
    }

    @Test
    fun `findByUserId - page beyond total returns empty list`() = runBlocking {
        val userId = Uuid.parse("33333333-3333-3333-3333-333333333333")

        (1..3).forEach { i ->
            repository.save(testTreatment(userId, treatedAt = Instant.parse("2024-01-${10 + i}T10:00:00Z")))
        }

        val result = repository.findByUserId(userId, page = 1, size = 3)

        assertEquals(emptyList(), result)
    }

    @Test
    fun `deleteAll - deletes matching treatment for correct user`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val t1 = repository.save(testTreatment(userId))
        val t2 = repository.save(testTreatment(userId))
        repository.deleteAll(listOf(t1.id), userId)
        val remaining = repository.findByUserId(userId, page = 0, size = 50)
        assertEquals(1, remaining.size)
        assertEquals(t2.id, remaining[0].id)
    }

    @Test
    fun `deleteAll - does not delete when userId does not match`() = runBlocking {
        val userA = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val userB = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val t = repository.save(testTreatment(userA))
        repository.deleteAll(listOf(t.id), userB) // wrong userId
        val remaining = repository.findByUserId(userA, page = 0, size = 50)
        assertEquals(1, remaining.size)
    }

    @Test
    fun `findLatestTimestampsByTypes - returns correct latest per type`() = runBlocking {
        val userId = Uuid.parse("44444444-4444-4444-4444-444444444444")

        // Two SENSOR_INSERT treatments — latest is Jan 20
        val sensorEarly = testTreatment(userId, TreatmentType.SENSOR_INSERT, Instant.parse("2024-01-10T08:00:00Z"))
        val sensorLatest = testTreatment(userId, TreatmentType.SENSOR_INSERT, Instant.parse("2024-01-20T08:00:00Z"))
        // One SITE_CHANGE treatment
        val siteChange = testTreatment(userId, TreatmentType.SITE_CHANGE, Instant.parse("2024-01-15T12:00:00Z"))
        repository.save(sensorEarly)
        repository.save(sensorLatest)
        repository.save(siteChange)

        val result = repository.findLatestTimestampsByTypes(
            userId,
            setOf(TreatmentType.SENSOR_INSERT, TreatmentType.SITE_CHANGE, TreatmentType.INSULIN_CHANGE),
        )

        assertEquals(2, result.size, "Expected 2 entries — INSULIN_CHANGE has no rows")
        assertEquals(sensorLatest.treatedAt, result[TreatmentType.SENSOR_INSERT])
        assertEquals(siteChange.treatedAt, result[TreatmentType.SITE_CHANGE])
        assertEquals(null, result[TreatmentType.INSULIN_CHANGE])
    }

    @Test
    fun `findLatestTimestampsByTypes - returns empty map when types set is empty`() = runBlocking {
        val userId = Uuid.parse("55555555-5555-5555-5555-555555555555")
        val result = repository.findLatestTimestampsByTypes(userId, emptySet())
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `findLatestTimestampsByTypes - ignores archived treatments`() = runBlocking {
        val userId = Uuid.parse("66666666-6666-6666-6666-666666666666")
        val archived = testTreatment(userId, TreatmentType.SENSOR_INSERT, Instant.parse("2024-01-10T08:00:00Z"))
            .copy(status = org.javafreedom.kdiab.treatments.domain.model.TreatmentStatus.ARCHIVED)
        repository.save(archived)

        val result = repository.findLatestTimestampsByTypes(userId, setOf(TreatmentType.SENSOR_INSERT))
        assertEquals(emptyMap(), result)
    }

    // ── findLatestTimestampByType (singular) ──────────────────────────────────

    @Test
    fun `findLatestTimestampByType - returns latest timestamp for type`() = runBlocking {
        val userId = Uuid.parse("77777777-7777-7777-7777-777777777777")
        val early  = testTreatment(userId, TreatmentType.SENSOR_INSERT, Instant.parse("2024-01-10T08:00:00Z"))
        val latest = testTreatment(userId, TreatmentType.SENSOR_INSERT, Instant.parse("2024-01-20T08:00:00Z"))
        repository.save(early)
        repository.save(latest)

        val result = repository.findLatestTimestampByType(userId, TreatmentType.SENSOR_INSERT)

        assertEquals(latest.treatedAt, result)
    }

    @Test
    fun `findLatestTimestampByType - returns null when no matching treatments exist`() = runBlocking {
        val userId = Uuid.parse("88888888-8888-8888-8888-888888888888")
        // Insert a treatment of a different type — should not be returned
        repository.save(testTreatment(userId, TreatmentType.BOLUS, Instant.parse("2024-01-10T08:00:00Z")))

        val result = repository.findLatestTimestampByType(userId, TreatmentType.SENSOR_INSERT)

        assertEquals(null, result)
    }

    @Test
    fun `findLatestTimestampByType - returns null when no treatments at all`() = runBlocking {
        val userId = Uuid.parse("99999999-9999-9999-9999-999999999999")

        val result = repository.findLatestTimestampByType(userId, TreatmentType.SENSOR_INSERT)

        assertEquals(null, result)
    }

    @Test
    fun `findLatestTimestampByType - ignores archived treatments`() = runBlocking {
        val userId   = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-000000000001")
        val archived = testTreatment(userId, TreatmentType.SITE_CHANGE, Instant.parse("2024-01-15T08:00:00Z"))
            .copy(status = org.javafreedom.kdiab.treatments.domain.model.TreatmentStatus.ARCHIVED)
        repository.save(archived)

        val result = repository.findLatestTimestampByType(userId, TreatmentType.SITE_CHANGE)

        assertEquals(null, result)
    }

    @Test
    fun `findLatestTimestampByType - returns latest when multiple active treatments exist`() = runBlocking {
        val userId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-000000000002")
        val t1 = testTreatment(userId, TreatmentType.INSULIN_CHANGE, Instant.parse("2024-01-01T08:00:00Z"))
        val t2 = testTreatment(userId, TreatmentType.INSULIN_CHANGE, Instant.parse("2024-01-15T08:00:00Z"))
        val t3 = testTreatment(userId, TreatmentType.INSULIN_CHANGE, Instant.parse("2024-01-10T08:00:00Z"))
        repository.save(t1); repository.save(t2); repository.save(t3)

        val result = repository.findLatestTimestampByType(userId, TreatmentType.INSULIN_CHANGE)

        assertEquals(t2.treatedAt, result)
    }
}

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
        val results = repository.findByUserId(userA)
        assertEquals(2, results.size)
        // ordered DESC: t2 first
        assertEquals(t2.id, results[0].id)
        assertEquals(t1.id, results[1].id)
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
    fun `deleteAll - deletes matching treatment for correct user`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val t1 = repository.save(testTreatment(userId))
        val t2 = repository.save(testTreatment(userId))
        repository.deleteAll(listOf(t1.id), userId)
        val remaining = repository.findByUserId(userId)
        assertEquals(1, remaining.size)
        assertEquals(t2.id, remaining[0].id)
    }

    @Test
    fun `deleteAll - does not delete when userId does not match`() = runBlocking {
        val userA = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val userB = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val t = repository.save(testTreatment(userA))
        repository.deleteAll(listOf(t.id), userB) // wrong userId
        val remaining = repository.findByUserId(userA)
        assertEquals(1, remaining.size)
    }
}

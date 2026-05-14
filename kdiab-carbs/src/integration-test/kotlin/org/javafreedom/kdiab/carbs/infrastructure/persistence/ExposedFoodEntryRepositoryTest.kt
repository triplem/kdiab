@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.assertFailsWith

/**
 * Repository-level integration tests for [ExposedFoodEntryRepository].
 *
 * Schema is bootstrapped via Liquibase (see [LiquibaseTestHelper]) against an H2 in-memory
 * database. Data is cleared before each test so that row-count assertions are not affected
 * by previously inserted rows.
 */
class ExposedFoodEntryRepositoryTest {

    companion object {
        private val db: Database = LiquibaseTestHelper.setup("carbs_repo_test")
    }

    private lateinit var repository: ExposedFoodEntryRepository

    @BeforeTest
    fun setup() {
        LiquibaseTestHelper.cleanData(db)
        repository = ExposedFoodEntryRepository()
    }

    private fun testEntry(
        userId: Uuid = Uuid.parse("11111111-1111-1111-1111-111111111111"),
        name: String = "White Rice",
        portionGrams: Double = 100.0,
        carbsPer100g: Double = 28.0,
    ): FoodEntry {
        val now = Clock.System.now()
        return FoodEntry(
            id = Uuid.random(),
            userId = userId,
            name = name,
            portionGrams = portionGrams,
            carbsPer100g = carbsPer100g,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `save - inserts and returns entry with all fields matching input`() = runBlocking {
        val entry = testEntry()

        val saved = repository.save(entry)

        assertEquals(entry.id, saved.id)
        assertEquals(entry.userId, saved.userId)
        assertEquals(entry.name, saved.name)
        assertEquals(entry.portionGrams, saved.portionGrams)
        assertEquals(entry.carbsPer100g, saved.carbsPer100g)
    }

    @Test
    fun `findByUserId - returns entries for correct user only`() = runBlocking {
        val userA = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val userB = Uuid.parse("22222222-2222-2222-2222-222222222222")

        repository.save(testEntry(userId = userA, name = "Apple"))
        repository.save(testEntry(userId = userA, name = "Banana"))
        repository.save(testEntry(userId = userB, name = "Carrot"))

        val results = repository.findByUserId(userA, page = 0, size = 10, nameFilter = null)

        assertEquals(2, results.size)
        assert(results.all { it.userId == userA })
    }

    @Test
    fun `findByUserId - pagination returns correct slice`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val names = listOf("Apple", "Banana", "Cherry", "Date", "Elderberry")
        names.forEach { repository.save(testEntry(userId = userId, name = it)) }

        val page0 = repository.findByUserId(userId, page = 0, size = 3, nameFilter = null)
        val page1 = repository.findByUserId(userId, page = 1, size = 3, nameFilter = null)

        assertEquals(3, page0.size)
        assertEquals(2, page1.size)
        // Results are ordered by name ASC — pages must be disjoint
        val page0Names = page0.map { it.name }.toSet()
        val page1Names = page1.map { it.name }.toSet()
        assertEquals(0, (page0Names intersect page1Names).size)
    }

    @Test
    fun `findByUserId - name filter returns only matching entries`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        repository.save(testEntry(userId = userId, name = "Brown Rice"))
        repository.save(testEntry(userId = userId, name = "White Rice"))
        repository.save(testEntry(userId = userId, name = "Pasta"))

        val results = repository.findByUserId(userId, page = 0, size = 10, nameFilter = "rice")

        assertEquals(2, results.size)
        assert(results.all { it.name.lowercase().contains("rice") })
    }

    @Test
    fun `countByUserId - returns correct total for user`() = runBlocking {
        val userA = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val userB = Uuid.parse("22222222-2222-2222-2222-222222222222")

        repository.save(testEntry(userId = userA))
        repository.save(testEntry(userId = userA))
        repository.save(testEntry(userId = userB))

        assertEquals(2L, repository.countByUserId(userA, nameFilter = null))
        assertEquals(1L, repository.countByUserId(userB, nameFilter = null))
    }

    @Test
    fun `findById - returns entry when id and userId match`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val saved = repository.save(testEntry(userId = userId))

        val found = repository.findById(saved.id, userId)

        assertNotNull(found)
        assertEquals(saved.id, found.id)
    }

    @Test
    fun `findById - returns null when userId does not match`() = runBlocking {
        val userA = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val userB = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val saved = repository.save(testEntry(userId = userA))

        val result = repository.findById(saved.id, userB)

        assertNull(result)
    }

    @Test
    fun `update - modifies name, portionGrams, and carbsPer100g`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val saved = repository.save(testEntry(userId = userId, name = "Old Name", portionGrams = 100.0, carbsPer100g = 20.0))

        val updated = repository.update(
            id = saved.id,
            userId = userId,
            name = "New Name",
            portionGrams = 150.0,
            carbsPer100g = 25.0,
        )

        assertEquals("New Name", updated.name)
        assertEquals(150.0, updated.portionGrams)
        assertEquals(25.0, updated.carbsPer100g)
    }

    @Test
    fun `update - throws ResourceNotFoundException for non-existent entry`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val nonExistentId = Uuid.random()

        assertFailsWith<ResourceNotFoundException> {
            repository.update(nonExistentId, userId, "X", 100.0, 10.0)
        }
    }

    @Test
    fun `delete - removes entry from repository`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val saved = repository.save(testEntry(userId = userId))

        repository.delete(saved.id, userId)

        val remaining = repository.findByUserId(userId, page = 0, size = 10, nameFilter = null)
        assertEquals(0, remaining.size)
    }

    @Test
    fun `delete - throws ResourceNotFoundException when entry not found`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")

        assertFailsWith<ResourceNotFoundException> {
            repository.delete(Uuid.random(), userId)
        }
    }

    @Test
    fun `findByUserId - page beyond total returns empty list`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        repository.save(testEntry(userId = userId))

        val result = repository.findByUserId(userId, page = 1, size = 10, nameFilter = null)

        assertEquals(emptyList(), result)
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry
import org.javafreedom.kdiab.carbs.domain.repository.FoodEntryRepository

class FoodEntryServiceTest {

    private val repo = mockk<FoodEntryRepository>()
    private val service = FoodEntryService(repo)

    private val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val foodId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    private fun testEntry() = FoodEntry(
        id = foodId,
        userId = userId,
        name = "White rice",
        portionGrams = 150.0,
        carbsPer100g = 28.0,
        createdAt = Instant.parse("2024-01-01T10:00:00Z"),
        updatedAt = Instant.parse("2024-01-01T10:00:00Z"),
    )

    @Test
    fun `getEntries delegates to repo and returns paged result`() = runTest {
        val entries = listOf(testEntry())
        coEvery { repo.findByUserId(userId, 0, 50, null) } returns entries
        coEvery { repo.countByUserId(userId, null) } returns 1L

        val result = service.getEntries(userId, 0, 50, null)

        assertEquals(entries, result.items)
        assertEquals(1L, result.totalCount)
        assertEquals(0, result.page)
        assertEquals(50, result.size)
        coVerify(exactly = 1) { repo.findByUserId(userId, 0, 50, null) }
        coVerify(exactly = 1) { repo.countByUserId(userId, null) }
    }

    @Test
    fun `createEntry saves and returns the entry`() = runTest {
        val entry = testEntry()
        coEvery { repo.save(entry) } returns entry

        val result = service.createEntry(entry)

        assertEquals(entry, result)
        coVerify(exactly = 1) { repo.save(entry) }
    }

    @Test
    fun `updateEntry returns updated entry`() = runTest {
        val updated = testEntry().copy(name = "Brown rice", portionGrams = 200.0)
        coEvery { repo.update(foodId, userId, "Brown rice", 200.0, 28.0) } returns updated

        val result = service.updateEntry(foodId, userId, "Brown rice", 200.0, 28.0)

        assertEquals(updated, result)
        coVerify(exactly = 1) { repo.update(foodId, userId, "Brown rice", 200.0, 28.0) }
    }

    @Test
    fun `deleteEntry calls repo delete`() = runTest {
        coEvery { repo.delete(foodId, userId) } just runs

        service.deleteEntry(foodId, userId)

        coVerify(exactly = 1) { repo.delete(foodId, userId) }
    }
}

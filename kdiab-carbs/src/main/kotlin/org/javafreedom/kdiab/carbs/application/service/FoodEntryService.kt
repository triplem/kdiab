@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.application.service

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry
import org.javafreedom.kdiab.carbs.domain.model.PagedFoodEntries
import org.javafreedom.kdiab.carbs.domain.repository.FoodEntryRepository

class FoodEntryService(private val repo: FoodEntryRepository) {

    suspend fun getEntries(userId: Uuid, page: Int, size: Int, nameFilter: String?): PagedFoodEntries {
        val items = repo.findByUserId(userId, page, size, nameFilter)
        val total = repo.countByUserId(userId, nameFilter)
        return PagedFoodEntries(items = items, page = page, size = size, totalCount = total)
    }

    suspend fun createEntry(entry: FoodEntry): FoodEntry = repo.save(entry)

    suspend fun updateEntry(
        id: Uuid,
        userId: Uuid,
        name: String,
        portionGrams: Double,
        carbsPer100g: Double,
    ): FoodEntry =
        repo.update(id, userId, name, portionGrams, carbsPer100g)

    suspend fun archiveEntry(id: Uuid, userId: Uuid): FoodEntry = repo.archive(id, userId)

    suspend fun deleteEntry(id: Uuid, userId: Uuid) = repo.delete(id, userId)
}

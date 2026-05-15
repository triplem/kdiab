@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.domain.repository

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry

interface FoodEntryRepository {
    suspend fun findByUserId(userId: Uuid, page: Int, size: Int, nameFilter: String?): List<FoodEntry>
    suspend fun countByUserId(userId: Uuid, nameFilter: String?): Long
    suspend fun findById(id: Uuid, userId: Uuid): FoodEntry?
    suspend fun save(entry: FoodEntry): FoodEntry
    suspend fun update(id: Uuid, userId: Uuid, name: String, portionGrams: Double, carbsPer100g: Double): FoodEntry
    suspend fun archive(id: Uuid, userId: Uuid): FoodEntry
    suspend fun delete(id: Uuid, userId: Uuid)
}

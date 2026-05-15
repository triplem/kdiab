@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val GRAMS_PER_100G = 100.0

enum class FoodEntryStatus {
    ACTIVE,
    ARCHIVED
}

data class FoodEntry(
    val id: Uuid,
    val userId: Uuid,
    val name: String,
    val portionGrams: Double,
    val carbsPer100g: Double,
    val status: FoodEntryStatus = FoodEntryStatus.ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val carbsForPortion: Double get() = portionGrams * carbsPer100g / GRAMS_PER_100G
}

data class PagedFoodEntries(
    val items: List<FoodEntry>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

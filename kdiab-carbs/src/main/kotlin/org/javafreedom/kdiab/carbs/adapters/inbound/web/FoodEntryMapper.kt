@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.adapters.inbound.web

import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.carbs.api.models.CreateFoodEntryRequest
import org.javafreedom.kdiab.carbs.api.models.FoodEntryResponse
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry

fun FoodEntry.toApi() = FoodEntryResponse(
    id = id.toString(),
    userId = userId.toString(),
    name = name,
    portionGrams = portionGrams,
    carbsPer100g = carbsPer100g,
    carbsForPortion = carbsForPortion,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

fun CreateFoodEntryRequest.toDomain(userId: Uuid): FoodEntry {
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

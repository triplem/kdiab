@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.carbs.api.models.CreateFoodEntryRequest
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry

class FoodEntryMapperTest {

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
    fun `toApi maps all fields correctly`() {
        val entry = testEntry()
        val api = entry.toApi()

        assertEquals(foodId.toString(), api.id)
        assertEquals(userId.toString(), api.userId)
        assertEquals("White rice", api.name)
        assertEquals(150.0, api.portionGrams)
        assertEquals(28.0, api.carbsPer100g)
        assertEquals(42.0, api.carbsForPortion)
        assertEquals("2024-01-01T10:00:00Z", api.createdAt)
        assertEquals("2024-01-01T10:00:00Z", api.updatedAt)
    }

    @Test
    fun `toDomain creates entry with correct values`() {
        val request = CreateFoodEntryRequest(
            name = "Brown rice",
            portionGrams = 200.0,
            carbsPer100g = 23.0,
        )
        val domain = request.toDomain(userId)

        assertNotNull(domain.id)
        assertEquals(userId, domain.userId)
        assertEquals("Brown rice", domain.name)
        assertEquals(200.0, domain.portionGrams)
        assertEquals(23.0, domain.carbsPer100g)
    }

    @Test
    fun `carbsForPortion is computed correctly`() {
        val entry = testEntry()
        // 150g * 28g/100g = 42g
        assertEquals(42.0, entry.carbsForPortion)
    }
}

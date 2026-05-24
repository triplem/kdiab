package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.FoodEntryResponse
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.FoodEntryStatus
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Food
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FoodMapperTest {

    private fun foodEntryResponse(
        id: String = "food-abc",
        name: String = "Apple",
        portionGrams: java.math.BigDecimal = java.math.BigDecimal("150"),
        carbsPer100g: java.math.BigDecimal = java.math.BigDecimal("14"),
        carbsForPortion: java.math.BigDecimal = java.math.BigDecimal("21"),
        createdAt: String = "2024-01-01T00:00:00Z",
        updatedAt: String = "2024-06-01T12:00:00Z",
    ) = FoodEntryResponse(
        id = id,
        userId = "user-1",
        name = name,
        portionGrams = portionGrams,
        carbsPer100g = carbsPer100g,
        carbsForPortion = carbsForPortion,
        status = FoodEntryStatus.ACTIVE,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    // ---- FoodEntryResponse.toNs3Food() ----

    @Test
    fun `toNs3Food maps identifier from id`() {
        val result = foodEntryResponse(id = "food-abc").toNs3Food()
        assertEquals("food-abc", result.identifier)
    }

    @Test
    fun `toNs3Food maps name`() {
        val result = foodEntryResponse(name = "Banana").toNs3Food()
        assertEquals("Banana", result.name)
    }

    @Test
    fun `toNs3Food maps carbs from carbsForPortion`() {
        val result = foodEntryResponse(carbsForPortion = java.math.BigDecimal("21.5")).toNs3Food()
        assertEquals(21.5, result.carbs)
    }

    @Test
    fun `toNs3Food maps portionSize from portionGrams`() {
        val result = foodEntryResponse(portionGrams = java.math.BigDecimal("150")).toNs3Food()
        assertEquals(150.0, result.portionSize)
    }

    @Test
    fun `toNs3Food maps srvCreated from createdAt as epoch millis`() {
        val result = foodEntryResponse(createdAt = "2024-01-01T00:00:00Z").toNs3Food()
        assertNotNull(result.srvCreated)
        assertEquals(1704067200000L, result.srvCreated)
    }

    @Test
    fun `toNs3Food maps srvModified from updatedAt as epoch millis`() {
        val result = foodEntryResponse(updatedAt = "2024-06-01T12:00:00Z").toNs3Food()
        assertNotNull(result.srvModified)
        assertEquals(1717243200000L, result.srvModified)
    }

    @Test
    fun `toNs3Food leaves fat protein energy and category as null`() {
        val result = foodEntryResponse().toNs3Food()
        assertNull(result.fat)
        assertNull(result.protein)
        assertNull(result.energy)
        assertNull(result.category)
    }

    // ---- Ns3Food.toCreateFoodRequest() ----

    @Test
    fun `toCreateFoodRequest preserves name`() {
        val food = Ns3Food(identifier = "id", name = "Apple", carbs = 21.0, portionSize = 150.0)
        val result = food.toCreateFoodRequest()
        assertEquals("Apple", result.name)
    }

    @Test
    fun `toCreateFoodRequest sets portionGrams`() {
        val food = Ns3Food(identifier = "id", name = "Apple", carbs = 21.0, portionSize = 150.0)
        val result = food.toCreateFoodRequest()
        assertEquals(java.math.BigDecimal(150.0), result.portionGrams)
    }

    @Test
    fun `toCreateFoodRequest calculates carbsPer100g correctly`() {
        // carbs=21, portionSize=150 → carbsPer100g = 21/150*100 = 14.0
        val food = Ns3Food(identifier = "id", name = "Apple", carbs = 21.0, portionSize = 150.0)
        val result = food.toCreateFoodRequest()
        assertEquals(14.0, result.carbsPer100g.toDouble(), 0.001)
    }

    @Test
    fun `toCreateFoodRequest uses 100 as default portion when portionSize is null`() {
        // carbs=30, portionSize=null → treated as 100 → carbsPer100g = 30/100*100 = 30.0
        val food = Ns3Food(identifier = "id", name = "Rice", carbs = 30.0, portionSize = null)
        val result = food.toCreateFoodRequest()
        assertEquals(java.math.BigDecimal(100.0), result.portionGrams)
        assertEquals(30.0, result.carbsPer100g.toDouble(), 0.001)
    }

    // ---- Ns3Food.toUpdateFoodRequest() ----

    @Test
    fun `toUpdateFoodRequest preserves name`() {
        val food = Ns3Food(identifier = "id", name = "Pear", carbs = 15.0, portionSize = 120.0)
        val result = food.toUpdateFoodRequest()
        assertEquals("Pear", result.name)
    }

    @Test
    fun `toUpdateFoodRequest calculates carbsPer100g correctly`() {
        // carbs=15, portionSize=120 → carbsPer100g = 15/120*100 = 12.5
        val food = Ns3Food(identifier = "id", name = "Pear", carbs = 15.0, portionSize = 120.0)
        val result = food.toUpdateFoodRequest()
        assertEquals(12.5, result.carbsPer100g.toDouble(), 0.001)
    }
}

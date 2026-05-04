@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProfileTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `should serialize and deserialize Profile correctly`() {
        val id = Uuid.parse("123e4567-e89b-12d3-a456-426614174000")
        val userId = Uuid.parse("123e4567-e89b-12d3-a456-426614174001")
        val now = Instant.parse("2023-10-01T10:00:00Z")
        val time = LocalTime(10, 0)

        val profile =
                Profile(
                        id = id,
                        userId = userId,
                        name = "Test Profile",
                        insulinType = "Fiasp",
                        durationOfAction = 180,
                        status = ProfileStatus.ACTIVE,
                        createdAt = now,
                        basal = listOf(BasalSegment(time, 1.0)),
                        icr = listOf(IcrSegment(time, 15.0)),
                        isf = listOf(IsfSegment(time, 50.0)),
                        targets = listOf(TargetSegment(time, 100.0, 120.0))
                )

        val jsonString = json.encodeToString(profile)

        // Print json for debugging if needed
        println(jsonString)

        val decodedProfile = json.decodeFromString<Profile>(jsonString)

        assertEquals(profile, decodedProfile)
        assertEquals(id, decodedProfile.id)
        assertEquals(userId, decodedProfile.userId)
        assertEquals(now, decodedProfile.createdAt)
        assertEquals(time, decodedProfile.basal[0].startTime)
    }
}

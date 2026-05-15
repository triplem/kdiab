@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.domain.model.User
import org.javafreedom.kdiab.users.domain.model.UserSettings

class UserMapperTest {

    private val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val now = Clock.System.now()

    private fun settings() = UserSettings(
        userId = userId,
        timezone = "Europe/Berlin",
        language = "de",
        timeFormat = 24,
        glucoseUnit = "mg/dL",
        weightUnit = "kg",
        alarmUrgentHigh = 260,
        alarmHigh = 200,
        alarmLow = 75,
        alarmUrgentLow = 55,
        createdAt = now,
        updatedAt = now,
    )

    private fun user(s: UserSettings? = settings()) = User(
        userId = userId,
        email = "test@example.com",
        displayName = "Test User",
        roles = setOf(Role.PATIENT),
        settings = s,
    )

    @Test
    fun `toResponse maps all User fields`() {
        val response = user().toResponse()
        assertEquals(userId.toString(), response.userId)
        assertEquals("test@example.com", response.email)
        assertEquals("Test User", response.displayName)
        assertEquals(listOf("PATIENT"), response.roles)
        assertNotNull(response.settings)
    }

    @Test
    fun `toResponse handles null settings`() {
        val response = user(null).toResponse()
        assertNull(response.settings)
    }

    @Test
    fun `UserSettings toResponse maps all fields`() {
        val response = settings().toResponse()
        assertEquals("Europe/Berlin", response.timezone)
        assertEquals("de", response.language)
        assertEquals(24, response.timeFormat)
        assertEquals("mg/dL", response.glucoseUnit)
        assertEquals("kg", response.weightUnit)
        assertEquals(260, response.alarmUrgentHigh)
        assertEquals(200, response.alarmHigh)
        assertEquals(75, response.alarmLow)
        assertEquals(55, response.alarmUrgentLow)
        assertNull(response.jwtBackedNote)
    }

    @Test
    fun `UserSettings toResponse includes jwtBackedNote when provided`() {
        val response = settings().toResponse(jwtBackedNote = "Takes effect on next login.")
        assertEquals("Takes effect on next login.", response.jwtBackedNote)
    }

    @Test
    fun `PatchSettingsRequest toPatch maps all fields`() {
        val req = PatchSettingsRequest(
            timezone = "UTC", language = "en", timeFormat = 12,
            glucoseUnit = "mmol/L", weightUnit = "lbs",
            alarmUrgentHigh = 300, alarmHigh = 250, alarmLow = 70, alarmUrgentLow = 50,
        )
        val patch = req.toPatch()
        assertEquals("UTC", patch.timezone)
        assertEquals("en", patch.language)
        assertEquals(12, patch.timeFormat)
        assertEquals("mmol/L", patch.glucoseUnit)
        assertEquals("lbs", patch.weightUnit)
        assertEquals(300, patch.alarmUrgentHigh)
    }

    @Test
    fun `PatchSettingsRequest toPatch handles all nulls`() {
        val patch = PatchSettingsRequest().toPatch()
        assertNull(patch.timezone)
        assertNull(patch.glucoseUnit)
    }
}

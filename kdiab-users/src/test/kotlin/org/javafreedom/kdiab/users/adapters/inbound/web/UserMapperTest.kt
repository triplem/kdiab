@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.domain.model.AlarmThresholds
import org.javafreedom.kdiab.users.domain.model.DiabetesProfile
import org.javafreedom.kdiab.users.domain.model.LocalePreferences
import org.javafreedom.kdiab.users.domain.model.UnitPreferences
import org.javafreedom.kdiab.users.domain.model.User
import org.javafreedom.kdiab.users.domain.model.UserSettings

class UserMapperTest {

    private val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val now = Clock.System.now()

    private fun settings() = UserSettings(
        userId = userId,
        createdAt = now,
        updatedAt = now,
        locale = LocalePreferences(timezone = "Europe/Berlin", language = "de", timeFormat = 24),
        units = UnitPreferences(glucoseUnit = "mg/dL", weightUnit = "kg"),
        alarms = AlarmThresholds(urgentHigh = 260, high = 200, low = 75, urgentLow = 55),
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
    fun `UserSettings toResponse maps all fields including nested sub-objects`() {
        val response = settings().toResponse()
        assertEquals("Europe/Berlin", response.locale.timezone)
        assertEquals("de", response.locale.language)
        assertEquals(24, response.locale.timeFormat)
        assertEquals("mg/dL", response.units.glucoseUnit)
        assertEquals("kg", response.units.weightUnit)
        assertNotNull(response.alarms)
        assertEquals(260, response.alarms!!.urgentHigh)
        assertEquals(200, response.alarms!!.high)
        assertEquals(75, response.alarms!!.low)
        assertEquals(55, response.alarms!!.urgentLow)
        assertNull(response.birthday)
        assertNull(response.diabetes.diabetesSince)
        assertNull(response.jwtBackedNote)
    }

    @Test
    fun `UserSettings toResponse maps birthday and diabetesSince when set`() {
        val settingsWithDates = settings().copy(
            birthday = LocalDate(1990, 5, 15),
            diabetes = DiabetesProfile(sensorDurationHours = 240, diabetesSince = 2010),
        )
        val response = settingsWithDates.toResponse()
        assertEquals("1990-05-15", response.birthday)
        assertEquals(2010, response.diabetes.diabetesSince)
    }

    @Test
    fun `UserSettings toResponse maps null alarms when no alarms set`() {
        val settingsNoAlarms = settings().copy(alarms = null)
        val response = settingsNoAlarms.toResponse()
        assertNull(response.alarms)
    }

    @Test
    fun `UserSettings toResponse includes jwtBackedNote when provided`() {
        val response = settings().toResponse(jwtBackedNote = "Takes effect on next login.")
        assertEquals("Takes effect on next login.", response.jwtBackedNote)
    }

    @Test
    fun `PatchSettingsRequest toPatch maps all nested fields`() {
        val req = PatchSettingsRequest(
            locale = LocalePreferencesPatch(timezone = "UTC", language = "en", timeFormat = 12),
            units = UnitPreferencesPatch(glucoseUnit = "mmol/L", weightUnit = "lbs"),
            alarms = AlarmThresholdsPatch(urgentHigh = 300, high = 250, low = 70, urgentLow = 50),
        )
        val patch = req.toPatch()
        assertEquals("UTC", patch.timezone)
        assertEquals("en", patch.language)
        assertEquals(12, patch.timeFormat)
        assertEquals("mmol/L", patch.glucoseUnit)
        assertEquals("lbs", patch.weightUnit)
        assertEquals(300, patch.alarmUrgentHigh)
        assertEquals(250, patch.alarmHigh)
        assertEquals(70, patch.alarmLow)
        assertEquals(50, patch.alarmUrgentLow)
    }

    @Test
    fun `PatchSettingsRequest toPatch maps birthday and diabetesSince`() {
        val req = PatchSettingsRequest(
            birthday = "1990-05-15",
            diabetes = DiabetesProfilePatch(diabetesSince = 2010),
        )
        val patch = req.toPatch()
        assertEquals(LocalDate(1990, 5, 15), patch.birthday)
        assertEquals(2010, patch.diabetesSince)
    }

    @Test
    fun `PatchSettingsRequest toPatch handles all nulls`() {
        val patch = PatchSettingsRequest().toPatch()
        assertNull(patch.timezone)
        assertNull(patch.glucoseUnit)
        assertNull(patch.birthday)
        assertNull(patch.diabetesSince)
    }
}

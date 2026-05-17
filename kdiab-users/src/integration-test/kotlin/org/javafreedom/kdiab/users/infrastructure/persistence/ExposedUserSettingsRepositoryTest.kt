@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.users.domain.model.UserSettings
import org.jetbrains.exposed.v1.jdbc.Database

class ExposedUserSettingsRepositoryTest {

    companion object {
        val db: Database = LiquibaseTestHelper.setup("users_settings_test")
    }

    private val repository = ExposedUserSettingsRepository()

    @BeforeTest
    fun setUp() = LiquibaseTestHelper.cleanData(db)

    private fun settings(userId: Uuid = Uuid.random()) = UserSettings(
        userId = userId,
        timezone = "Europe/Berlin",
        language = "de",
        timeFormat = 24,
        glucoseUnit = "mmol/L",
        weightUnit = "kg",
        createdAt = Instant.parse("2024-06-01T00:00:00Z"),
        updatedAt = Instant.parse("2024-06-01T00:00:00Z"),
    )

    @Test
    fun `save and findByUserId returns stored settings`() = runBlocking {
        val s = settings()
        repository.save(s)
        val found = repository.findByUserId(s.userId)
        assertNotNull(found)
        assertEquals(s.timezone, found.timezone)
        assertEquals(s.glucoseUnit, found.glucoseUnit)
        assertEquals(s.weightUnit, found.weightUnit)
        assertEquals(s.language, found.language)
        assertEquals(s.sensorDurationHours, found.sensorDurationHours)
    }

    @Test
    fun `save persists custom sensorDurationHours and retrieves it`() = runBlocking {
        val s = settings().copy(sensorDurationHours = 336)
        repository.save(s)
        val found = repository.findByUserId(s.userId)
        assertNotNull(found)
        assertEquals(336, found.sensorDurationHours)
    }

    @Test
    fun `findByUserId returns null when not found`() = runBlocking {
        assertNull(repository.findByUserId(Uuid.random()))
    }

    @Test
    fun `save is idempotent - upserts on same userId`() = runBlocking {
        val userId = Uuid.random()
        repository.save(settings(userId))
        repository.save(settings(userId).copy(timezone = "UTC"))
        val found = repository.findByUserId(userId)
        assertEquals("UTC", found?.timezone)
    }

    @Test
    fun `delete removes settings`() = runBlocking {
        val s = settings()
        repository.save(s)
        repository.delete(s.userId)
        assertNull(repository.findByUserId(s.userId))
    }
}

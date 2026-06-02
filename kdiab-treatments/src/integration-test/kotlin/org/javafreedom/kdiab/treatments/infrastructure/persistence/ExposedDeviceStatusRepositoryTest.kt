@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.treatments.domain.model.DeviceStatus
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Repository-level integration tests for [ExposedDeviceStatusRepository].
 *
 * Schema is bootstrapped via Liquibase (see [LiquibaseTestHelper]) against an H2 in-memory
 * database. Data is cleared before each test so that assertions are not affected by previously
 * inserted rows.
 */
class ExposedDeviceStatusRepositoryTest {

    private lateinit var repository: ExposedDeviceStatusRepository

    companion object {
        private val db: Database = LiquibaseTestHelper.setup("device_status_repo")
    }

    @BeforeTest
    fun setup() {
        LiquibaseTestHelper.cleanData(db)
        repository = ExposedDeviceStatusRepository()
    }

    private fun testDeviceStatus(
        userId: Uuid = Uuid.parse("11111111-1111-1111-1111-111111111111"),
        recordedAt: Instant = Instant.parse("2024-01-15T10:00:00Z"),
    ) = DeviceStatus(
        id = Uuid.random(),
        userId = userId,
        recordedAt = recordedAt,
        createdAt = Instant.parse("2024-01-15T10:00:00Z"),
        device = "AAPS",
        pumpName = "Dana-i",
        reservoirUnits = 150.0,
        batteryLevel = 85,
        pumpConnected = true,
    )

    @Test
    fun `save - returns saved device status with all fields preserved`() = runBlocking {
        val input = testDeviceStatus()
        val saved = repository.save(input)

        assertEquals(input.id, saved.id)
        assertEquals(input.userId, saved.userId)
        assertEquals(input.device, saved.device)
        assertEquals(input.pumpName, saved.pumpName)
        assertEquals(input.reservoirUnits, saved.reservoirUnits)
        assertEquals(input.batteryLevel, saved.batteryLevel)
        assertEquals(input.pumpConnected, saved.pumpConnected)
    }

    @Test
    fun `save - returns saved device status when optional fields are null`() = runBlocking {
        val input = testDeviceStatus().copy(
            pumpName = null,
            reservoirUnits = null,
            batteryLevel = null,
            pumpConnected = null,
        )
        val saved = repository.save(input)

        assertEquals(input.id, saved.id)
        assertNull(saved.pumpName)
        assertNull(saved.reservoirUnits)
        assertNull(saved.batteryLevel)
        assertNull(saved.pumpConnected)
    }

    @Test
    fun `findLatestByUserId - returns most recent entry for user`() = runBlocking {
        val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val early  = testDeviceStatus(userId, Instant.parse("2024-01-10T08:00:00Z"))
        val latest = testDeviceStatus(userId, Instant.parse("2024-01-20T08:00:00Z"))
        repository.save(early)
        repository.save(latest)

        val result = repository.findLatestByUserId(userId)

        assertNotNull(result)
        assertEquals(latest.id, result.id)
    }

    @Test
    fun `findLatestByUserId - returns null when no entries exist for user`() = runBlocking {
        val userId = Uuid.parse("22222222-2222-2222-2222-222222222222")

        val result = repository.findLatestByUserId(userId)

        assertNull(result)
    }

    @Test
    fun `findLatestByUserId - returns only the entry for the requested user`() = runBlocking {
        val userA = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val userB = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val dsA = testDeviceStatus(userA, Instant.parse("2024-01-15T10:00:00Z"))
        val dsB = testDeviceStatus(userB, Instant.parse("2024-01-16T10:00:00Z"))
        repository.save(dsA)
        repository.save(dsB)

        val resultA = repository.findLatestByUserId(userA)
        val resultB = repository.findLatestByUserId(userB)

        assertNotNull(resultA)
        assertNotNull(resultB)
        assertEquals(dsA.id, resultA.id)
        assertEquals(dsB.id, resultB.id)
    }
}

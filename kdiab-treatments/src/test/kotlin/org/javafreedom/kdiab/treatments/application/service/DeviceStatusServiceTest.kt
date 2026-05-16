@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.treatments.domain.model.DeviceStatus
import org.javafreedom.kdiab.treatments.domain.repository.DeviceStatusRepository
import kotlin.time.Instant

class DeviceStatusServiceTest {

    private val repo = mockk<DeviceStatusRepository>()
    private val service = DeviceStatusService(repo)

    private val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val recordedAt = Instant.parse("2026-05-16T10:00:00Z")

    @Test
    fun `saveDeviceStatus extracts all fields from JsonObject`() = runTest {
        val data = buildJsonObject {
            put("device", "AAPS 3.2.0")
            put("pumpName", "Dana RS")
            put("reservoirUnits", 142.5)
            put("batteryLevel", 87)
            put("pumpConnected", true)
        }
        val slot = slot<DeviceStatus>()
        coEvery { repo.save(capture(slot)) } answers { slot.captured }

        val result = service.saveDeviceStatus(userId, recordedAt, data)

        assertEquals(userId, result.userId)
        assertEquals(recordedAt, result.recordedAt)
        assertEquals("AAPS 3.2.0", result.device)
        assertEquals("Dana RS", result.pumpName)
        assertEquals(142.5, result.reservoirUnits)
        assertEquals(87, result.batteryLevel)
        assertEquals(true, result.pumpConnected)
        coVerify(exactly = 1) { repo.save(any()) }
    }

    @Test
    fun `saveDeviceStatus uses unknown when device field absent`() = runTest {
        val data = buildJsonObject { put("reservoirUnits", 50.0) }
        val slot = slot<DeviceStatus>()
        coEvery { repo.save(capture(slot)) } answers { slot.captured }

        val result = service.saveDeviceStatus(userId, recordedAt, data)

        assertEquals("unknown", result.device)
        assertNull(result.pumpName)
        assertNull(result.batteryLevel)
        assertNull(result.pumpConnected)
    }

    @Test
    fun `saveDeviceStatus handles optional fields absent`() = runTest {
        val data = buildJsonObject { put("device", "xDrip+") }
        val slot = slot<DeviceStatus>()
        coEvery { repo.save(capture(slot)) } answers { slot.captured }

        val result = service.saveDeviceStatus(userId, recordedAt, data)

        assertEquals("xDrip+", result.device)
        assertNull(result.pumpName)
        assertNull(result.reservoirUnits)
        assertNull(result.batteryLevel)
        assertNull(result.pumpConnected)
    }

    @Test
    fun `getLatestDeviceStatus returns null when no status exists`() = runTest {
        coEvery { repo.findLatestByUserId(userId) } returns null

        val result = service.getLatestDeviceStatus(userId)

        assertNull(result)
    }

    @Test
    fun `getLatestDeviceStatus returns the status from repository`() = runTest {
        val expected = DeviceStatus(
            id = Uuid.random(),
            userId = userId,
            recordedAt = recordedAt,
            createdAt = recordedAt,
            device = "AAPS 3.2.0",
            pumpName = "Dana RS",
            reservoirUnits = 100.0,
            batteryLevel = 90,
            pumpConnected = true,
        )
        coEvery { repo.findLatestByUserId(userId) } returns expected

        val result = service.getLatestDeviceStatus(userId)

        assertEquals(expected, result)
    }
}

package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentType
import org.javafreedom.kdiab.nightscout.domain.model.Ns3DeviceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeviceStatusMapperTest {

    private fun treatmentResponse(
        id: String = "t-123",
        treatedAt: String = "2024-01-01T00:00:00Z",
        data: kotlinx.serialization.json.JsonObject = buildJsonObject {},
    ) = TreatmentResponse(
        id = id,
        userId = "user-1",
        treatedAt = treatedAt,
        createdAt = treatedAt,
        type = TreatmentType.DEVICE_STATUS,
        data = data,
        status = TreatmentResponse.Status.ACTIVE,
    )

    @Test
    fun `toNs3DeviceStatus maps id and timestamps`() {
        val response = treatmentResponse(id = "ds-1", treatedAt = "2024-01-01T00:00:00Z")
        val result = response.toNs3DeviceStatus()
        assertEquals("ds-1", result.identifier)
        assertEquals("2024-01-01T00:00:00Z", result.dateString)
        assertEquals(1704067200000L, result.date)
    }

    @Test
    fun `toNs3DeviceStatus extracts device name from data`() {
        val data = buildJsonObject { put("device", "my-pump") }
        val result = treatmentResponse(data = data).toNs3DeviceStatus()
        assertEquals("my-pump", result.device)
    }

    @Test
    fun `toNs3DeviceStatus extracts battery level from data`() {
        val data = buildJsonObject { put("batteryLevel", 85) }
        val result = treatmentResponse(data = data).toNs3DeviceStatus()
        assertEquals(85, result.uploaderBattery)
    }

    @Test
    fun `toNs3DeviceStatus builds pump map with name and reservoir`() {
        val data = buildJsonObject {
            put("pumpName", "Dana-i")
            put("reservoirUnits", 120.5)
            put("batteryLevel", 90)
        }
        val result = treatmentResponse(data = data).toNs3DeviceStatus()
        assertNotNull(result.pump)
        assertEquals(JsonPrimitive("Dana-i"), result.pump!!["name"])
        assertEquals(JsonPrimitive(120.5), result.pump!!["reservoir"])
    }

    @Test
    fun `toNs3DeviceStatus sets pump status normal when pumpConnected true`() {
        val data = buildJsonObject { put("pumpConnected", "true") }
        val result = treatmentResponse(data = data).toNs3DeviceStatus()
        val statusObj = result.pump?.get("status")
        assertNotNull(statusObj)
    }

    @Test
    fun `toNs3DeviceStatus returns null pump when no pump fields present`() {
        val data = buildJsonObject { put("device", "uploader") }
        val result = treatmentResponse(data = data).toNs3DeviceStatus()
        assertNull(result.pump)
    }

    @Test
    fun `toNs3DeviceStatus handles empty data gracefully`() {
        val result = treatmentResponse(data = buildJsonObject {}).toNs3DeviceStatus()
        assertNull(result.device)
        assertNull(result.uploaderBattery)
        assertNull(result.pump)
    }

    @Test
    fun `toCreateTreatmentRequest produces DEVICE_STATUS type`() {
        val ds = Ns3DeviceStatus(
            identifier = "ds-1",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            device = "my-pump",
            uploaderBattery = 80,
        )
        val request = ds.toCreateTreatmentRequest()
        assertEquals(TreatmentType.DEVICE_STATUS, request.type)
        assertEquals("2024-01-01T00:00:00Z", request.treatedAt)
    }

    @Test
    fun `toCreateTreatmentRequest includes device in data`() {
        val ds = Ns3DeviceStatus(
            identifier = "ds-1",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            device = "xDrip",
        )
        val request = ds.toCreateTreatmentRequest()
        assertEquals("xDrip", request.data["device"]?.let { (it as? JsonPrimitive)?.content })
    }

    @Test
    fun `toCreateTreatmentRequest includes uploaderBattery as batteryLevel`() {
        val ds = Ns3DeviceStatus(
            identifier = "ds-1",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            uploaderBattery = 75,
        )
        val request = ds.toCreateTreatmentRequest()
        assertNotNull(request.data["batteryLevel"])
    }
}

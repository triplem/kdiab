package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentType
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Treatment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TreatmentMapperTest {

    private fun treatmentResponse(
        id: String = "t1",
        type: TreatmentType = TreatmentType.BOLUS,
        treatedAt: String = "2024-01-01T12:00:00Z",
        data: kotlinx.serialization.json.JsonObject = buildJsonObject { put("insulin", 2.5) },
        notes: String? = null,
    ) = TreatmentResponse(
        id = id,
        userId = "user1",
        treatedAt = treatedAt,
        createdAt = treatedAt,
        type = type,
        data = data,
        status = TreatmentResponse.Status.ACTIVE,
        notes = notes,
    )

    private fun ns3Treatment(
        identifier: String = "t1",
        eventType: String = "Bolus",
        date: Long = 1704110400000L,
        dateString: String = "2024-01-01T12:00:00Z",
        insulin: Double? = 2.5,
        carbs: Double? = null,
        notes: String? = null,
    ) = Ns3Treatment(
        identifier = identifier,
        date = date,
        dateString = dateString,
        eventType = eventType,
        insulin = insulin,
        carbs = carbs,
        notes = notes,
    )

    // ── TreatmentResponse.toNs3Treatment() ───────────────────────────────────

    @Test
    fun `toNs3Treatment maps BOLUS with insulin to Bolus eventType`() {
        val response = treatmentResponse(type = TreatmentType.BOLUS, data = buildJsonObject { put("insulin", 2.5) })
        val result = response.toNs3Treatment()
        assertEquals("t1", result.identifier)
        assertEquals("Bolus", result.eventType)
        assertEquals(2.5, result.insulin)
        assertNull(result.carbs)
    }

    @Test
    fun `toNs3Treatment maps CARBS with carbs field`() {
        val response = treatmentResponse(
            type = TreatmentType.CARBS,
            data = buildJsonObject { put("carbs", 45.0) },
        )
        val result = response.toNs3Treatment()
        assertEquals("Carbs", result.eventType)
        assertEquals(45.0, result.carbs)
        assertNull(result.insulin)
    }

    @Test
    fun `toNs3Treatment sets date from treatedAt epoch millis`() {
        val response = treatmentResponse(treatedAt = "2024-01-01T00:00:00Z")
        val result = response.toNs3Treatment()
        assertEquals(1704067200000L, result.date)
        assertEquals("2024-01-01T00:00:00Z", result.dateString)
    }

    @Test
    fun `toNs3Treatment falls back to type value for unmapped type`() {
        // COMBO_BOLUS maps to "Combo Bolus"
        val response = treatmentResponse(type = TreatmentType.COMBO_BOLUS)
        val result = response.toNs3Treatment()
        assertEquals("Combo Bolus", result.eventType)
    }

    @Test
    fun `toNs3Treatment preserves notes`() {
        val response = treatmentResponse(notes = "post-meal correction")
        val result = response.toNs3Treatment()
        assertEquals("post-meal correction", result.notes)
    }

    @Test
    fun `toNs3Treatment reads insulin from units field as fallback`() {
        val response = treatmentResponse(
            type = TreatmentType.BOLUS,
            data = buildJsonObject { put("units", 1.5) },
        )
        val result = response.toNs3Treatment()
        assertEquals(1.5, result.insulin)
    }

    @Test
    fun `toNs3Treatment sets srvCreated and srvModified to same millis as date`() {
        val response = treatmentResponse(treatedAt = "2024-01-01T00:00:00Z")
        val result = response.toNs3Treatment()
        assertEquals(result.date, result.srvCreated)
        assertEquals(result.date, result.srvModified)
    }

    // ── Ns3Treatment.toCreateTreatmentRequest() ───────────────────────────────

    @Test
    fun `toCreateTreatmentRequest maps Bolus eventType to BOLUS TreatmentType`() {
        val treatment = ns3Treatment(eventType = "Bolus", insulin = 3.0)
        val request = treatment.toCreateTreatmentRequest()
        assertNotNull(request)
        assertEquals(TreatmentType.BOLUS, request.type)
        assertEquals(3.0, request.data["insulin"]?.let {
            kotlinx.serialization.json.JsonPrimitive(it.toString())
            it.toString().toDoubleOrNull()
        })
    }

    @Test
    fun `toCreateTreatmentRequest maps Carbs eventType to CARBS TreatmentType`() {
        val treatment = ns3Treatment(eventType = "Carbs", carbs = 30.0, insulin = null)
        val request = treatment.toCreateTreatmentRequest()
        assertNotNull(request)
        assertEquals(TreatmentType.CARBS, request.type)
    }

    @Test
    fun `toCreateTreatmentRequest returns null for unknown eventType`() {
        val treatment = ns3Treatment(eventType = "UnknownEvent")
        val result = treatment.toCreateTreatmentRequest()
        assertNull(result)
    }

    @Test
    fun `toCreateTreatmentRequest preserves notes`() {
        val treatment = ns3Treatment(eventType = "Bolus", notes = "correction dose")
        val request = treatment.toCreateTreatmentRequest()
        assertNotNull(request)
        assertEquals("correction dose", request.notes)
    }

    @Test
    fun `toCreateTreatmentRequest maps Correction Bolus to CORRECTION_BOLUS`() {
        val treatment = ns3Treatment(eventType = "Correction Bolus", insulin = 1.5)
        val request = treatment.toCreateTreatmentRequest()
        assertNotNull(request)
        assertEquals(TreatmentType.CORRECTION_BOLUS, request.type)
    }

    // ── Ns3Treatment.toUpdateTreatmentRequest() ───────────────────────────────

    @Test
    fun `toUpdateTreatmentRequest builds request with insulin and carbs`() {
        val treatment = ns3Treatment(eventType = "Bolus", insulin = 2.0, carbs = null)
        val request = treatment.toUpdateTreatmentRequest()
        assertEquals("2024-01-01T12:00:00Z", request.treatedAt)
        assertNotNull(request.data["insulin"])
        assertNull(request.data["carbs"])
    }

    @Test
    fun `toUpdateTreatmentRequest omits null fields from data`() {
        val treatment = ns3Treatment(eventType = "Carbs", insulin = null, carbs = 50.0)
        val request = treatment.toUpdateTreatmentRequest()
        assertNull(request.data["insulin"])
        assertNotNull(request.data["carbs"])
    }

    @Test
    fun `toUpdateTreatmentRequest preserves notes`() {
        val treatment = ns3Treatment(eventType = "Bolus", notes = "updated dose")
        val request = treatment.toUpdateTreatmentRequest()
        assertEquals("updated dose", request.notes)
    }
}

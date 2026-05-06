@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web

import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.javafreedom.kdiab.treatments.api.models.CreateTreatmentRequest
import org.javafreedom.kdiab.treatments.api.models.TreatmentResponse
import org.javafreedom.kdiab.treatments.api.models.TreatmentType as ApiTreatmentType
import org.javafreedom.kdiab.treatments.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.treatments.domain.model.Treatment as DomainTreatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType

// Conversion constants — named to make the registry self-documenting
private const val MINUTES_PER_HOUR   = 60.0
private const val MMOL_TO_MGDL       = 18.0182
private const val ROUNDING_SCALE     = 1_000
private const val SECONDS_TO_MINUTES = 1.0 / MINUTES_PER_HOUR
private const val MINUTES_TO_HOURS   = 1.0 / MINUTES_PER_HOUR

/**
 * Describes how a numeric field with an optional companion unit field should be
 * normalised to a canonical storage unit.
 *
 * @param unitField      Name of the JSON field that carries the unit string (e.g. "durationUnit").
 * @param defaultUnit    Unit assumed when [unitField] is absent — value is stored as-is.
 * @param toCanonical    Map of lowercase unit name → multiplication factor to reach canonical unit.
 */
private data class FieldConversion(
    val unitField: String,
    val defaultUnit: String,
    val toCanonical: Map<String, Double>,
)

/**
 * Registry of fields that require unit normalisation before storage.
 *
 * - **duration** → minutes  (frontend may send hours or seconds with `durationUnit`)
 * - **glucose**  → mg/dL    (frontend may send mmol/L with `units`)
 * - **absorptionTime** → hours (frontend may send minutes with `absorptionTimeUnit`)
 */
private val payloadConversions: Map<String, FieldConversion> = mapOf(
    "duration" to FieldConversion(
        unitField = "durationUnit",
        defaultUnit = "minutes",
        toCanonical = mapOf(
            "minutes" to 1.0,
            "hours"   to MINUTES_PER_HOUR,
            "seconds" to SECONDS_TO_MINUTES,
        ),
    ),
    "glucose" to FieldConversion(
        unitField = "units",
        defaultUnit = "mgdl",
        toCanonical = mapOf(
            "mgdl"   to 1.0,
            "mg/dl"  to 1.0,
            "mmol"   to MMOL_TO_MGDL,
            "mmol/l" to MMOL_TO_MGDL,
        ),
    ),
    "absorptionTime" to FieldConversion(
        unitField = "absorptionTimeUnit",
        defaultUnit = "hours",
        toCanonical = mapOf(
            "hours"   to 1.0,
            "minutes" to MINUTES_TO_HOURS,
        ),
    ),
)

/**
 * Normalises all registered numeric fields in [data] to their canonical storage units.
 *
 * For each registered field:
 * - If the companion unit field is absent the value is assumed to already be in the canonical
 *   unit and is left unchanged.
 * - If the companion unit field is present the value is multiplied by the appropriate factor
 *   and the unit field is removed from the result.
 * - If the unit string is not recognised the field is left unchanged (safe fallback).
 */
internal fun normalizePayload(data: JsonObject): JsonObject {
    val result = data.toMutableMap()
    payloadConversions.forEach { (fieldName, conv) ->
        val rawValue = (result[fieldName] as? JsonPrimitive)?.content?.toDoubleOrNull()
        val unitRaw  = (result[conv.unitField] as? JsonPrimitive)?.content?.lowercase()
        val factor   = if (unitRaw != null) conv.toCanonical[unitRaw] else null
        if (rawValue != null && factor != null) {
            result[fieldName] = jsonPrimitiveOf(rawValue * factor)
            result.remove(conv.unitField)
        }
    }
    return JsonObject(result)
}

/** Serialises a Double as a compact JsonPrimitive (long when the value is whole). */
private fun jsonPrimitiveOf(value: Double): JsonPrimitive {
    val rounded = (value * ROUNDING_SCALE).roundToLong() / ROUNDING_SCALE.toDouble()
    val asLong  = rounded.toLong()
    return if (asLong.toDouble() == rounded) JsonPrimitive(asLong) else JsonPrimitive(rounded)
}

fun CreateTreatmentRequest.toDomain(targetUserId: Uuid): DomainTreatment = DomainTreatment(
    id        = Uuid.random(),
    userId    = targetUserId,
    treatedAt = try {
        Instant.parse(this.treatedAt)
    } catch (e: IllegalArgumentException) {
        throw BusinessValidationException(
            "Invalid treatedAt timestamp: '${this.treatedAt}' is not a valid ISO-8601 instant",
            e
        )
    },
    createdAt = Clock.System.now(),
    type      = TreatmentType.valueOf(this.type.name),
    data      = normalizePayload(this.`data`),
    notes     = this.notes,
)

fun DomainTreatment.toApi(): TreatmentResponse = TreatmentResponse(
    id        = this.id.toString(),
    userId    = this.userId.toString(),
    treatedAt = this.treatedAt.toString(),
    createdAt = this.createdAt.toString(),
    type      = ApiTreatmentType.valueOf(this.type.name),
    `data`    = this.data,
    notes     = this.notes,
    status    = TreatmentResponse.Status.valueOf(this.status.name),
)

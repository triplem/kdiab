@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web

import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.javafreedom.kdiab.measures.api.models.CreateMeasureRequest
import org.javafreedom.kdiab.measures.api.models.MeasureResponse
import org.javafreedom.kdiab.measures.api.models.MeasureSource as ApiMeasureSource
import org.javafreedom.kdiab.measures.api.models.MeasureStatus as ApiMeasureStatus
import org.javafreedom.kdiab.measures.api.models.MeasureType as ApiMeasureType
import org.javafreedom.kdiab.measures.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.measures.domain.model.Measure as DomainMeasure
import org.javafreedom.kdiab.measures.domain.model.MeasureSource
import org.javafreedom.kdiab.measures.domain.model.MeasureStatus
import org.javafreedom.kdiab.measures.domain.model.MeasureType

private const val MMOL_TO_MGDL = 18.0182
private const val KG_TO_LBS = 2.20462
private const val ROUNDING_FACTOR = 10.0

// Normalize an incoming payload to canonical storage units (mg/dL for glucose, kg for weight).
fun CreateMeasureRequest.toDomain(targetUserId: Uuid): DomainMeasure = DomainMeasure(
    id = Uuid.random(),
    userId = targetUserId,
    measuredAt = try {
        Instant.parse(this.measuredAt)
    } catch (e: IllegalArgumentException) {
        throw BusinessValidationException(
            "Invalid measuredAt timestamp: '${this.measuredAt}' is not a valid ISO-8601 instant",
            e
        )
    },
    createdAt = Clock.System.now(),
    type = MeasureType.valueOf(this.type.name),
    source = MeasureSource.valueOf(this.source.name),
    data = normalizeToCanonical(this.type.name, this.`data`),
    status = MeasureStatus.ACTIVE
)

fun DomainMeasure.toApi(glucoseUnit: String = "mg/dL", weightUnit: String = "kg"): MeasureResponse =
    MeasureResponse(
        id = this.id.toString(),
        userId = this.userId.toString(),
        measuredAt = this.measuredAt.toString(),
        createdAt = this.createdAt.toString(),
        type = ApiMeasureType.valueOf(this.type.name),
        source = ApiMeasureSource.valueOf(this.source.name),
        `data` = convertFromCanonical(this.type.name, this.data, glucoseUnit, weightUnit),
        status = ApiMeasureStatus.valueOf(this.status.name)
    )

// Convert incoming payload to canonical storage units.
// Glucose types (BGM, CGM, BG_CHECK): store in mg/dL.
// WEIGHT: store in kg.
// Other types: store as-is.
@Suppress("ReturnCount")
private fun normalizeToCanonical(typeName: String, data: JsonObject): JsonObject {
    return when (typeName) {
        "BGM", "CGM", "BG_CHECK" -> {
            val unit = data["unit"]?.jsonPrimitive?.content ?: "mg/dL"
            val value = data["value"]?.jsonPrimitive?.double ?: return data
            val canonical = if (unit == "mmol/L") mmolToMgdl(value) else value
            buildJsonObject {
                put("value", JsonPrimitive(canonical))
                // CGM may have a trend field
                data["trend"]?.let { put("trend", it) }
            }
        }
        "WEIGHT" -> {
            val unit = data["unit"]?.jsonPrimitive?.content ?: "kg"
            val value = data["value"]?.jsonPrimitive?.double ?: return data
            val canonical = if (unit == "lbs") lbsToKg(value) else value
            buildJsonObject { put("value", JsonPrimitive(canonical)) }
        }
        else -> data
    }
}

// Convert canonical payload to the user's preferred units for the response.
@Suppress("ReturnCount")
private fun convertFromCanonical(
    typeName: String,
    data: JsonObject,
    glucoseUnit: String,
    weightUnit: String,
): JsonObject {
    return when (typeName) {
        "BGM", "CGM", "BG_CHECK" -> {
            val value = data["value"]?.jsonPrimitive?.double ?: return data
            val converted = if (glucoseUnit == "mmol/L") mgdlToMmol(value) else roundMgdl(value)
            buildJsonObject {
                put("value", JsonPrimitive(converted))
                put("unit", JsonPrimitive(glucoseUnit))
                data["trend"]?.let { put("trend", it) }
            }
        }
        "WEIGHT" -> {
            val value = data["value"]?.jsonPrimitive?.double ?: return data
            val converted = if (weightUnit == "lbs") kgToLbs(value) else roundKg(value)
            buildJsonObject {
                put("value", JsonPrimitive(converted))
                put("unit", JsonPrimitive(weightUnit))
            }
        }
        "BLOOD_PRESSURE" -> buildJsonObject {
            put("systolic", data["systolic"] ?: JsonPrimitive(0))
            put("diastolic", data["diastolic"] ?: JsonPrimitive(0))
            put("unit", JsonPrimitive("mmHg"))
        }
        "PULSE" -> buildJsonObject {
            put("value", data["value"] ?: JsonPrimitive(0))
            put("unit", JsonPrimitive("bpm"))
        }
        "KETONE_CHECK" -> buildJsonObject {
            put("value", data["value"] ?: JsonPrimitive(0))
            put("unit", JsonPrimitive("mmol/L"))
            data["method"]?.let { put("method", it) }
        }
        else -> data
    }
}

private fun mmolToMgdl(mmol: Double): Double = (mmol * MMOL_TO_MGDL).roundToInt().toDouble()
private fun mgdlToMmol(mgdl: Double): Double = ((mgdl / MMOL_TO_MGDL) * ROUNDING_FACTOR).roundToInt() / ROUNDING_FACTOR
private fun roundMgdl(mgdl: Double): Double = mgdl.roundToInt().toDouble()

private fun lbsToKg(lbs: Double): Double = ((lbs / KG_TO_LBS) * ROUNDING_FACTOR).roundToInt() / ROUNDING_FACTOR
private fun kgToLbs(kg: Double): Double = ((kg * KG_TO_LBS) * ROUNDING_FACTOR).roundToInt() / ROUNDING_FACTOR
private fun roundKg(kg: Double): Double = ((kg * ROUNDING_FACTOR).roundToInt() / ROUNDING_FACTOR)

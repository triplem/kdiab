package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.CreateMeasureRequest
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.UpdateMeasureRequest
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry

private const val MMOL_TO_MGDL = 18.0
private const val MMOL_ROUND_FACTOR = 10.0

fun MeasureResponse.toNs3Entry(glucoseUnit: String): Ns3Entry {
    val millis = runCatching { Instant.parse(measuredAt).toEpochMilliseconds() }.getOrDefault(0L)
    val rawSgv = data["sgv"]?.jsonPrimitive?.runCatching { int }?.getOrNull()
        ?: data["value"]?.jsonPrimitive?.runCatching { int }?.getOrNull()
    val sgv: Double? = rawSgv?.let {
        if (glucoseUnit == "mmol/L") {
            Math.round(it / MMOL_TO_MGDL * MMOL_ROUND_FACTOR) / MMOL_ROUND_FACTOR
        } else {
            it.toDouble()
        }
    }
    val direction = data["direction"]?.jsonPrimitive?.content
    return Ns3Entry(
        identifier = id,
        date = millis,
        dateString = measuredAt,
        type = if (type.value == "CGM") "sgv" else "mbg",
        sgv = sgv,
        direction = direction,
        srvCreated = millis,
        srvModified = millis,
    )
}

fun Ns3Entry.toCreateMeasureRequest(glucoseUnit: String): CreateMeasureRequest? {
    val measureType = when (type) {
        "sgv" -> MeasureType.CGM
        "mbg" -> MeasureType.BGM
        else -> return null
    }
    val storedSgv = sgv?.let {
        if (glucoseUnit == "mmol/L") (it * MMOL_TO_MGDL).toInt() else it.toInt()
    }
    val data = buildJsonObject {
        storedSgv?.let { put("sgv", it) }
        direction?.let { put("direction", it) }
    }
    return CreateMeasureRequest(
        measuredAt = dateString,
        type = measureType,
        source = MeasureSource.NIGHTSCOUT,
        data = data,
    )
}

fun Ns3Entry.toUpdateMeasureRequest(glucoseUnit: String): UpdateMeasureRequest {
    val storedSgv = sgv?.let {
        if (glucoseUnit == "mmol/L") (it * MMOL_TO_MGDL).toInt() else it.toInt()
    }
    val data = buildJsonObject {
        storedSgv?.let { put("sgv", it) }
        direction?.let { put("direction", it) }
    }
    return UpdateMeasureRequest(measuredAt = dateString, data = data)
}

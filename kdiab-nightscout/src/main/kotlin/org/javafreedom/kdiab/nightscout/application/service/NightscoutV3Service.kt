package org.javafreedom.kdiab.nightscout.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import org.javafreedom.kdiab.nightscout.adapters.inbound.web.Ns3SearchParams
import org.javafreedom.kdiab.nightscout.adapters.inbound.web.toCreateMeasureRequest
import org.javafreedom.kdiab.nightscout.adapters.inbound.web.toNs3Entry
import org.javafreedom.kdiab.nightscout.adapters.inbound.web.toUpdateMeasureRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry

private val logger = KotlinLogging.logger {}

class NightscoutV3Service(private val measuresClient: MeasuresClient) {

    @Suppress("LongParameterList")
    suspend fun searchEntries(
        userId: String,
        authorization: String,
        correlationId: String,
        params: Ns3SearchParams,
        glucoseUnit: String,
    ): List<Ns3Entry> {
        val from = params.filters["date"]?.let { (op, v) ->
            if (op == "\$gte") epochMsToIso(v.toLongOrNull()) else null
        }
        val to = params.filters["date"]?.let { (op, v) ->
            if (op == "\$lte") epochMsToIso(v.toLongOrNull()) else null
        }
        return measuresClient.getMeasures(userId, authorization, correlationId, from, to)
            .map { it.toNs3Entry(glucoseUnit) }
            .let { entries ->
                if (params.sortDesc) entries.sortedByDescending { e -> e.date }
                else entries.sortedBy { e -> e.date }
            }
            .drop(params.skip)
            .take(params.limit)
    }

    @Suppress("LongParameterList")
    suspend fun getEntry(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        glucoseUnit: String,
    ): Ns3Entry? = measuresClient.getMeasure(userId, authorization, correlationId, id)?.toNs3Entry(glucoseUnit)

    @Suppress("LongParameterList")
    suspend fun createEntry(
        userId: String,
        authorization: String,
        correlationId: String,
        entry: Ns3Entry,
        glucoseUnit: String,
    ): Ns3Entry {
        val request = entry.toCreateMeasureRequest(glucoseUnit) ?: error("Unsupported entry type: ${entry.type}")
        measuresClient.postMeasure(userId, authorization, correlationId, request)
        logger.info { "Created v3 entry type=${entry.type} userId=$userId" }
        return entry
    }

    @Suppress("LongParameterList")
    suspend fun updateEntry(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        entry: Ns3Entry,
        glucoseUnit: String,
    ): Ns3Entry {
        val request = entry.toUpdateMeasureRequest(glucoseUnit)
        return measuresClient.updateMeasure(userId, authorization, correlationId, id, request).toNs3Entry(glucoseUnit)
    }

    @Suppress("LongParameterList")
    suspend fun deleteEntry(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        permanent: Boolean,
    ) {
        measuresClient.deleteMeasure(userId, authorization, correlationId, id, permanent)
        logger.info { "Deleted v3 entry id=$id userId=$userId permanent=$permanent" }
    }
}

private fun epochMsToIso(epochMs: Long?): String? =
    epochMs?.let { Instant.fromEpochMilliseconds(it).toString() }

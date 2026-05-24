package org.javafreedom.kdiab.nightscout.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toCreateMeasureRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toCreateTreatmentRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toNs3Entry
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toNs3Treatment
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toUpdateMeasureRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toUpdateTreatmentRequest
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry
import org.javafreedom.kdiab.nightscout.domain.model.Ns3SearchParams
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Treatment

private val logger = KotlinLogging.logger {}

class NightscoutV3Service(
    private val measuresClient: MeasuresClient,
    private val treatmentsClient: TreatmentsClient,
) {

    @Suppress("LongParameterList")
    suspend fun searchEntries(
        userId: String,
        authorization: String,
        correlationId: String,
        params: Ns3SearchParams,
        glucoseUnit: String,
    ): List<Ns3Entry> {
        val dateFilters = params.filters["date"] ?: emptyList()
        val from = dateFilters.firstOrNull { (op, _) -> op == "\$gte" }
            ?.second?.toLongOrNull()?.let { epochMsToIso(it) }
        val to = dateFilters.firstOrNull { (op, _) -> op == "\$lte" }
            ?.second?.toLongOrNull()?.let { epochMsToIso(it) }
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
        val created = measuresClient.postMeasure(userId, authorization, correlationId, request)
        logger.info { "Created v3 entry type=${entry.type} userId=$userId serverId=${created.id}" }
        return entry.copy(identifier = created.id)
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

    @Suppress("LongParameterList")
    suspend fun searchTreatments(
        userId: String,
        authorization: String,
        correlationId: String,
        params: Ns3SearchParams,
    ): List<Ns3Treatment> {
        val dateFilters = params.filters["date"] ?: emptyList()
        val from = dateFilters.firstOrNull { (op, _) -> op == "\$gte" }
            ?.second?.toLongOrNull()?.let { epochMsToIso(it) }
        val to = dateFilters.firstOrNull { (op, _) -> op == "\$lte" }
            ?.second?.toLongOrNull()?.let { epochMsToIso(it) }
        return treatmentsClient.getTreatments(userId, authorization, correlationId, from, to)
            .map { it.toNs3Treatment() }
            .let { treatments ->
                if (params.sortDesc) treatments.sortedByDescending { t -> t.date }
                else treatments.sortedBy { t -> t.date }
            }
            .drop(params.skip)
            .take(params.limit)
    }

    @Suppress("LongParameterList")
    suspend fun getTreatment(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
    ): Ns3Treatment? = treatmentsClient.getTreatment(userId, authorization, correlationId, id)?.toNs3Treatment()

    @Suppress("LongParameterList")
    suspend fun createTreatment(
        userId: String,
        authorization: String,
        correlationId: String,
        treatment: Ns3Treatment,
    ): Ns3Treatment {
        val request = treatment.toCreateTreatmentRequest()
            ?: error("Unsupported treatment eventType: ${treatment.eventType}")
        val created = treatmentsClient.postTreatment(userId, authorization, correlationId, request)
        logger.info { "Created v3 treatment eventType=${treatment.eventType} userId=$userId" }
        return created.toNs3Treatment()
    }

    @Suppress("LongParameterList")
    suspend fun updateTreatment(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        treatment: Ns3Treatment,
    ): Ns3Treatment {
        val request = treatment.toUpdateTreatmentRequest()
        return treatmentsClient.updateTreatment(userId, authorization, correlationId, id, request).toNs3Treatment()
    }

    @Suppress("LongParameterList")
    suspend fun deleteTreatment(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        permanent: Boolean,
    ) {
        treatmentsClient.deleteTreatment(userId, authorization, correlationId, id, permanent)
        logger.info { "Deleted v3 treatment id=$id userId=$userId permanent=$permanent" }
    }
}

private fun epochMsToIso(epochMs: Long?): String? =
    epochMs?.let { Instant.fromEpochMilliseconds(it).toString() }

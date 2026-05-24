package org.javafreedom.kdiab.nightscout.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.CarbsClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toCreateFoodRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toCreateProfileRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toNs3Profile
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toCreateMeasureRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toCreateTreatmentRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toNs3Entry
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toNs3Food
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toNs3Treatment
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toUpdateFoodRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toUpdateMeasureRequest
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.toUpdateTreatmentRequest
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Food
import org.javafreedom.kdiab.nightscout.domain.model.Ns3HistoryResult
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Profile
import org.javafreedom.kdiab.nightscout.domain.model.Ns3SearchParams
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Settings
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Treatment

private val logger = KotlinLogging.logger {}

private const val MINUTES_PER_HOUR_SERVICE = 60.0

class NightscoutV3Service(
    private val measuresClient: MeasuresClient,
    private val treatmentsClient: TreatmentsClient,
    private val carbsClient: CarbsClient,
    private val profilesClient: ProfilesClient,
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
    suspend fun historyEntries(
        userId: String,
        authorization: String,
        correlationId: String,
        lastModified: Long?,
        glucoseUnit: String,
    ): Ns3HistoryResult<Ns3Entry> {
        val fromIso = epochMsToIso(lastModified)
        val entries = measuresClient.getMeasures(userId, authorization, correlationId, from = fromIso)
            .map { it.toNs3Entry(glucoseUnit) }
        val newestSrvModified = entries.mapNotNull { it.srvModified }.maxOrNull()
        return Ns3HistoryResult(status = 200, result = entries, lastModified = newestSrvModified)
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

    @Suppress("LongParameterList")
    suspend fun searchFood(
        userId: String,
        authorization: String,
        correlationId: String,
        params: Ns3SearchParams,
    ): List<Ns3Food> {
        val allFood = mutableListOf<Ns3Food>()
        var page = 0
        var totalFetched = 0
        var totalCount = Int.MAX_VALUE
        while (totalFetched < totalCount) {
            val paged = carbsClient.listFood(userId, authorization, correlationId, page)
            totalCount = paged.totalCount
            if (paged.items.isEmpty()) break
            allFood.addAll(paged.items.map { it.toNs3Food() })
            totalFetched += paged.items.size
            page++
        }
        return allFood.drop(params.skip).take(params.limit)
    }

    @Suppress("LongParameterList")
    suspend fun getFood(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
    ): Ns3Food? = carbsClient.getFood(userId, authorization, correlationId, id)?.toNs3Food()

    @Suppress("LongParameterList")
    suspend fun createFood(
        userId: String,
        authorization: String,
        correlationId: String,
        food: Ns3Food,
    ): Ns3Food {
        val request = food.toCreateFoodRequest()
        val created = carbsClient.createFood(userId, authorization, correlationId, request)
        logger.info { "Created v3 food identifier=${created.id} userId=$userId" }
        return created.toNs3Food()
    }

    @Suppress("LongParameterList")
    suspend fun updateFood(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        food: Ns3Food,
    ): Ns3Food {
        val request = food.toUpdateFoodRequest()
        val updated = carbsClient.updateFood(userId, authorization, correlationId, id, request)
        logger.info { "Updated v3 food id=$id userId=$userId" }
        return updated.toNs3Food()
    }

    @Suppress("LongParameterList")
    suspend fun deleteFood(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        permanent: Boolean,
    ) {
        carbsClient.deleteFood(userId, authorization, correlationId, id, permanent)
        logger.info { "Deleted v3 food id=$id userId=$userId permanent=$permanent" }
    }

    @Suppress("LongParameterList")
    suspend fun getSettings(
        userId: String,
        authorization: String,
        correlationId: String,
        glucoseUnit: String,
    ): Ns3Settings = Ns3Settings(
        identifier = userId,
        units = glucoseUnit,
        timeZone = "UTC",
    )

    @Suppress("LongParameterList")
    suspend fun searchProfiles(
        userId: String,
        authorization: String,
        correlationId: String,
        params: Ns3SearchParams,
    ): List<Ns3Profile> =
        profilesClient.listProfiles(userId, authorization, correlationId)
            .map { it.toNs3Profile() }
            .sortedByDescending { it.srvModified }
            .drop(params.skip)
            .take(params.limit)

    @Suppress("LongParameterList")
    suspend fun getProfile(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
    ): Ns3Profile? = profilesClient.getProfile(userId, authorization, correlationId, id)?.toNs3Profile()

    @Suppress("LongParameterList")
    suspend fun createProfile(
        userId: String,
        authorization: String,
        correlationId: String,
        profile: Ns3Profile,
    ): Ns3Profile {
        val request = profile.toCreateProfileRequest()
        val created = profilesClient.createProfile(userId, authorization, correlationId, request)
        logger.info { "Created v3 profile name=${profile.defaultProfile} userId=$userId serverId=${created.id}" }
        return created.toNs3Profile()
    }

    @Suppress("LongParameterList")
    suspend fun updateProfile(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        profile: Ns3Profile,
    ): Ns3Profile {
        val existing = profilesClient.getProfile(userId, authorization, correlationId, id)
            ?: error("Profile not found: $id")
        val updateRequest = existing.copy(
            name = profile.defaultProfile,
            durationOfAction = (profile.dia * MINUTES_PER_HOUR_SERVICE).toInt(),
        )
        val updated = profilesClient.updateProfile(userId, authorization, correlationId, id, updateRequest)
        logger.info { "Updated v3 profile id=$id userId=$userId newId=${updated.id}" }
        return updated.toNs3Profile()
    }

    @Suppress("LongParameterList")
    suspend fun deleteProfile(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        permanent: Boolean,
    ) {
        require(!permanent) { "Permanent deletion is not supported for profiles; use soft-archive only" }
        profilesClient.archiveProfile(userId, authorization, correlationId, id)
        logger.info { "Archived v3 profile id=$id userId=$userId" }
    }
}

private fun epochMsToIso(epochMs: Long?): String? =
    epochMs?.let { Instant.fromEpochMilliseconds(it).toString() }

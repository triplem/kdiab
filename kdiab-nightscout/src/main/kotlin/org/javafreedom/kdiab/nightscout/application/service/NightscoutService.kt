package org.javafreedom.kdiab.nightscout.application.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.javafreedom.kdiab.nightscout.adapters.inbound.web.toNightscoutEntry
import org.javafreedom.kdiab.nightscout.adapters.inbound.web.toNightscoutTreatment
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutEntry
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutTreatment

class NightscoutService(
    private val measuresClient: MeasuresClient,
    private val treatmentsClient: TreatmentsClient,
) {
    @Suppress("LongParameterList")
    suspend fun getEntries(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
        count: Int = DEFAULT_COUNT,
    ): List<NightscoutEntry> {
        val measures = measuresClient.getMeasures(
            userId = userId,
            authorization = authorization,
            correlationId = correlationId,
            from = from,
            to = to,
        )
        return measures
            .mapNotNull { it.toNightscoutEntry() }
            .sortedByDescending { it.date }
            .take(count)
    }

    @Suppress("LongParameterList")
    suspend fun getTreatments(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
        count: Int = DEFAULT_COUNT,
    ): List<NightscoutTreatment> = coroutineScope {
        val treatments = treatmentsClient.getTreatments(
            userId = userId,
            authorization = authorization,
            correlationId = correlationId,
            from = from,
            to = to,
        )
        treatments
            .mapNotNull { it.toNightscoutTreatment() }
            .sortedByDescending { it.mills }
            .take(count)
    }

    companion object {
        private const val DEFAULT_COUNT = 288
    }
}

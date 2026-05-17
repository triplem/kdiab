package org.javafreedom.kdiab.analyze.application.port.outbound

import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentType

interface TreatmentsPort {
    suspend fun getTreatments(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
    ): List<TreatmentResponse>

    // Six parameters are required: HTTP context (authorization, correlationId), resource owner (userId),
    // filter (type), and optional date range (from, to). Splitting the date range into a wrapper object
    // would add indirection with no benefit here.
    @Suppress("LongParameterList")
    suspend fun getTreatmentsByType(
        userId: String,
        authorization: String,
        correlationId: String,
        type: TreatmentType,
        from: String? = null,
        to: String? = null,
    ): List<TreatmentResponse>
}

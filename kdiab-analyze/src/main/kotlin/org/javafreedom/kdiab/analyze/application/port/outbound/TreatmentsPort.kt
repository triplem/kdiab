package org.javafreedom.kdiab.analyze.application.port.outbound

import org.javafreedom.kdiab.analyze.domain.model.DeviceAge
import org.javafreedom.kdiab.analyze.domain.model.DeviceStatus
import org.javafreedom.kdiab.analyze.domain.model.UpstreamTreatment

interface TreatmentsPort {
    suspend fun getTreatments(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
    ): List<UpstreamTreatment>

    // Six parameters are required: HTTP context (authorization, correlationId), resource owner (userId),
    // filter (type), and optional date range (from, to). Splitting the date range into a wrapper object
    // would add indirection with no benefit here.
    @Suppress("LongParameterList")
    suspend fun getTreatmentsByType(
        userId: String,
        authorization: String,
        correlationId: String,
        type: String,
        from: String? = null,
        to: String? = null,
    ): List<UpstreamTreatment>

    suspend fun getDeviceAge(
        userId: String,
        authorization: String,
        correlationId: String,
    ): DeviceAge

    suspend fun getLatestDeviceStatus(
        userId: String,
        authorization: String,
        correlationId: String,
    ): DeviceStatus?
}

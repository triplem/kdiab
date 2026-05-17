package org.javafreedom.kdiab.analyze.application.port.outbound

import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.analyze.domain.model.DeviceAge
import org.javafreedom.kdiab.analyze.domain.model.DeviceStatus

interface TreatmentsPort {
    suspend fun getTreatments(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
    ): List<TreatmentResponse>

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

package org.javafreedom.kdiab.analyze.application.service

import org.javafreedom.kdiab.analyze.domain.model.DeviceUsageResult

interface DeviceUsageOperation {
    suspend fun compute(
        userId: String,
        days: Int,
        authorization: String,
        correlationId: String,
    ): DeviceUsageResult
}

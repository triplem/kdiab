package org.javafreedom.kdiab.analyze.application.service

import kotlinx.datetime.TimeZone
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult

// Default TIR thresholds in mg/dL — ADA/EASD consensus targets for T1D
private const val DEFAULT_TIR_LOW = 70.0
private const val DEFAULT_TIR_HIGH = 180.0

interface AnalyticsOperation {
    suspend fun getAnalysisThresholds(
        userId: String,
        authorization: String,
        correlationId: String,
    ): Pair<Double, Double>

    @Suppress("LongParameterList")
    suspend fun getHba1c(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        tirLow: Double = DEFAULT_TIR_LOW,
        tirHigh: Double = DEFAULT_TIR_HIGH,
    ): Hba1cResult

    @Suppress("LongParameterList")
    suspend fun getAgp(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        tirLow: Double = DEFAULT_TIR_LOW,
        tirHigh: Double = DEFAULT_TIR_HIGH,
        // TODO(#859): wire timezone from JWT claim once UserPrincipal carries a timezone field
        timeZone: TimeZone = TimeZone.UTC,
    ): AgpResult
}

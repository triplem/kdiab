package org.javafreedom.kdiab.analyze.application.service

import kotlinx.datetime.TimeZone
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.CgpResult
import org.javafreedom.kdiab.analyze.domain.model.DailyStatsResult
import org.javafreedom.kdiab.analyze.domain.model.DailyTrendResult
import org.javafreedom.kdiab.analyze.domain.model.GlucoseDistributionResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult
import org.javafreedom.kdiab.analyze.domain.model.ReportSummaryResult

// Default TIR thresholds in mg/dL — ADA/EASD consensus targets for T1D
private const val DEFAULT_TIR_LOW = 70.0
private const val DEFAULT_TIR_HIGH = 180.0

interface AnalyticsOperation {
    suspend fun getAnalysisThresholds(
        userId: String,
        authorization: String,
        correlationId: String,
    ): Pair<Double, Double>

    /**
     * Pre-warms the in-process CGM cache for the given user and time window.
     * Calling this before [getHba1c] or [getAgp] allows the route layer to
     * run the kdiab-measures fetch in parallel with [getAnalysisThresholds]
     * (→ kdiab-users). Both upstream calls are independent; the cache ensures
     * the subsequent service call is served from memory.
     */
    suspend fun preFetchCgmMeasures(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        correlationId: String,
    )

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
        timeZone: TimeZone = TimeZone.UTC,
    ): AgpResult

    @Suppress("LongParameterList")
    suspend fun getDailyTrend(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        timeZone: TimeZone = TimeZone.UTC,
    ): DailyTrendResult

    @Suppress("LongParameterList")
    suspend fun getDailyStats(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        timeZone: TimeZone = TimeZone.UTC,
    ): DailyStatsResult

    @Suppress("LongParameterList")
    suspend fun getGlucoseDistribution(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
    ): GlucoseDistributionResult

    @Suppress("LongParameterList")
    suspend fun getReportSummary(
        userId: String,
        displayName: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        timeZone: TimeZone = TimeZone.UTC,
    ): ReportSummaryResult

    @Suppress("LongParameterList")
    suspend fun getCgp(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
    ): CgpResult
}

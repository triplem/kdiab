package org.javafreedom.kdiab.calc.application.service

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.javafreedom.kdiab.calc.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.IcrSegment
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.IsfSegment
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.TargetSegment
import org.javafreedom.kdiab.calc.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.calc.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.calc.domain.model.CgmTrend
import org.javafreedom.kdiab.calc.domain.model.DoseBreakdown
import org.javafreedom.kdiab.calc.domain.model.DoseRequest
import org.javafreedom.kdiab.calc.domain.model.DoseResult

private const val MMOL_TO_MGDL_FACTOR = 18.0
private const val HYPOGLYCEMIA_THRESHOLD = 70.0
private const val HIGH_DOSE_THRESHOLD = 20.0
private const val TREND_DOUBLE_ADJUSTMENT = 2.0
private const val TREND_SINGLE_ADJUSTMENT = 1.0
private const val TREND_FORTY_FIVE_ADJUSTMENT = 0.5
private const val ROUND_TWO_DECIMAL_FACTOR = 100.0

@Suppress("LongMethod")
class DoseCalculationService(private val profilesClient: ProfilesClient) {

    suspend fun calculateDose(
        userId: String,
        request: DoseRequest,
        authorization: String,
        correlationId: String,
    ): DoseResult {
        val profile = profilesClient.getActiveProfile(userId, authorization, correlationId)
            ?: throw ResourceNotFoundException("No active profile found for user")

        val refTime: LocalTime = if (request.useProfileTime != null) {
            Instant.parse(request.useProfileTime).toLocalDateTime(TimeZone.UTC).time
        } else {
            Clock.System.now().toLocalDateTime(TimeZone.UTC).time
        }

        val bgMgDl = if (request.glucoseUnit.equals("mmol/L", ignoreCase = true) ||
            request.glucoseUnit.equals("mmol/l", ignoreCase = true)
        ) {
            request.currentBg * MMOL_TO_MGDL_FACTOR
        } else {
            request.currentBg
        }

        val isf = lookupIsfSegment(profile.isf.orEmpty(), refTime)
        val icr = lookupIcrSegment(profile.icr.orEmpty(), refTime)
        val target = lookupTargetSegment(profile.targets.orEmpty(), refTime)

        val correctionDose = (bgMgDl - target) / isf
        val carbDose = if (request.carbsGrams > 0) request.carbsGrams / icr else 0.0
        val trendAdj = trendAdjustment(request.trend)
        val total = maxOf(0.0, correctionDose + carbDose + trendAdj)

        val warnings = buildList {
            if (bgMgDl < HYPOGLYCEMIA_THRESHOLD) {
                add("BG is hypoglycemic — no correction dose recommended; treat hypo first")
            } else if (correctionDose < 0 && carbDose > 0) {
                add("BG is below target; carb dose only")
            }
            if (total > HIGH_DOSE_THRESHOLD) {
                add("Calculated dose is unusually high — please verify inputs")
            }
        }

        return DoseResult(
            correctionDose = round2(correctionDose),
            carbDose = round2(carbDose),
            trendAdjustment = trendAdj,
            totalRecommended = round2(total),
            breakdown = DoseBreakdown(
                currentBgMgDl = round2(bgMgDl),
                targetBgMgDl = round2(target),
                isf = isf,
                icr = icr,
                trend = request.trend,
                carbsGrams = request.carbsGrams,
            ),
            profileId = profile.id,
            warnings = warnings,
        )
    }

    private fun lookupIsfSegment(segments: List<IsfSegment>, refTime: LocalTime): Double {
        if (segments.isEmpty()) throw BusinessValidationException("Profile has no ISF segments")
        val match = segments
            .filter { parseSegmentTime(it.startTime) <= refTime }
            .maxByOrNull { parseSegmentTime(it.startTime) }
            ?: segments.last()
        return match.`value`
    }

    private fun lookupIcrSegment(segments: List<IcrSegment>, refTime: LocalTime): Double {
        if (segments.isEmpty()) throw BusinessValidationException("Profile has no ICR segments")
        val match = segments
            .filter { parseSegmentTime(it.startTime) <= refTime }
            .maxByOrNull { parseSegmentTime(it.startTime) }
            ?: segments.last()
        return match.`value`
    }

    private fun lookupTargetSegment(segments: List<TargetSegment>, refTime: LocalTime): Double {
        if (segments.isEmpty()) throw BusinessValidationException("Profile has no target segments")
        val match = segments
            .filter { parseSegmentTime(it.startTime) <= refTime }
            .maxByOrNull { parseSegmentTime(it.startTime) }
            ?: segments.last()
        return (match.low + match.high) / 2.0
    }

    private fun parseSegmentTime(time: String): LocalTime {
        val parts = time.split(":")
        return LocalTime(parts[0].toInt(), parts[1].toInt())
    }

    private fun trendAdjustment(trend: CgmTrend) = when (trend) {
        CgmTrend.DOUBLE_UP -> TREND_DOUBLE_ADJUSTMENT
        CgmTrend.SINGLE_UP -> TREND_SINGLE_ADJUSTMENT
        CgmTrend.FORTY_FIVE_UP -> TREND_FORTY_FIVE_ADJUSTMENT
        CgmTrend.FLAT -> 0.0
        CgmTrend.FORTY_FIVE_DOWN -> -TREND_FORTY_FIVE_ADJUSTMENT
        CgmTrend.SINGLE_DOWN -> -TREND_SINGLE_ADJUSTMENT
        CgmTrend.DOUBLE_DOWN -> -TREND_DOUBLE_ADJUSTMENT
        CgmTrend.NONE -> 0.0
    }

    private fun round2(v: Double) = Math.round(v * ROUND_TWO_DECIMAL_FACTOR) / ROUND_TWO_DECIMAL_FACTOR
}

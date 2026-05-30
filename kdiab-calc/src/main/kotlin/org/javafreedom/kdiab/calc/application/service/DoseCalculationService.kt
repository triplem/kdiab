package org.javafreedom.kdiab.calc.application.service

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.calc.domain.model.CgmTrend
import org.javafreedom.kdiab.calc.domain.model.DoseBreakdown
import org.javafreedom.kdiab.calc.domain.model.DoseRequest
import org.javafreedom.kdiab.calc.domain.model.DoseResult
import org.javafreedom.kdiab.calc.domain.model.GlucoseTarget
import org.javafreedom.kdiab.calc.domain.model.IcrRatio
import org.javafreedom.kdiab.calc.domain.model.IsfRatio
import org.javafreedom.kdiab.calc.domain.repository.ProfilesPort

private const val MMOL_TO_MGDL_FACTOR = 18.0
private const val HYPOGLYCEMIA_THRESHOLD = 70.0
private const val HIGH_DOSE_THRESHOLD = 20.0
private const val MAX_ABSOLUTE_DOSE = 30.0
private const val TREND_DOUBLE_MGDL_OFFSET = 30.0     // expected BG rise for DoubleUp
private const val TREND_SINGLE_MGDL_OFFSET = 20.0
private const val TREND_FORTY_FIVE_MGDL_OFFSET = 10.0
private const val ROUND_TWO_DECIMAL_FACTOR = 100.0

@Suppress("LongMethod")
class DoseCalculationService(private val profilesPort: ProfilesPort) {

    suspend fun calculateDose(
        userId: String,
        request: DoseRequest,
        authorization: String,
        correlationId: String,
    ): DoseResult {
        val profile = profilesPort.getActiveProfile(userId, authorization, correlationId)
            ?: throw ResourceNotFoundException("No active profile found for user")

        val profileTimeZone = profile.timeZone?.let { TimeZone.of(it) } ?: TimeZone.UTC
        val refTime: LocalTime = if (request.useProfileTime != null) {
            Instant.parse(request.useProfileTime).toLocalDateTime(profileTimeZone).time
        } else {
            Clock.System.now().toLocalDateTime(profileTimeZone).time
        }

        val bgMgDl = if (request.glucoseUnit.equals("mmol/L", ignoreCase = true)) {
            request.currentBg * MMOL_TO_MGDL_FACTOR
        } else {
            request.currentBg
        }

        val isf = lookupIsfSegment(profile.isf, refTime)
        val icr = lookupIcrSegment(profile.icr, refTime)
        val target = lookupTargetSegment(profile.targets, refTime)

        if (isf <= 0.0) throw BusinessValidationException("ISF value must be positive")
        if (request.carbsGrams > 0 && icr <= 0.0) {
            throw BusinessValidationException("ICR value must be positive when carbs are entered")
        }

        val rawCorrection = (bgMgDl - target) / isf
        val isHypoglycemic = bgMgDl < HYPOGLYCEMIA_THRESHOLD
        val correctionDose = if (isHypoglycemic) 0.0 else maxOf(0.0, rawCorrection - request.activeIob)
        val carbDose = if (!isHypoglycemic && request.carbsGrams > 0) request.carbsGrams / icr else 0.0
        val trendAdj = if (isHypoglycemic) 0.0 else trendAdjustment(request.trend, isf)
        val total = if (isHypoglycemic) 0.0 else maxOf(0.0, correctionDose + carbDose + trendAdj)

        val warnings = buildList {
            if (isHypoglycemic) {
                add("BG is hypoglycemic — no correction dose recommended; treat hypo first")
            } else if (rawCorrection < 0 && carbDose > 0) {
                add("BG is below target; carb dose only")
            }
            if (iobCoversFullCorrection(request.activeIob, rawCorrection, correctionDose, bgMgDl, target)) {
                add("IOB covers the full correction — no additional correction dose recommended")
            }
            if (total > HIGH_DOSE_THRESHOLD) {
                add("Calculated dose is unusually high — please verify inputs")
            }
        }

        if (total > MAX_ABSOLUTE_DOSE) {
            throw BusinessValidationException(
                "Calculated dose of ${round2(total)} U exceeds the maximum allowed single dose of" +
                    " $MAX_ABSOLUTE_DOSE U — check your profile configuration"
            )
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

    private fun <T> lookupSegment(
        segments: List<T>,
        refTime: LocalTime,
        getStartTime: (T) -> String,
        label: String,
    ): T {
        if (segments.isEmpty()) throw BusinessValidationException("Profile has no $label segments")
        val parsed = segments.map { it to parseSegmentTime(getStartTime(it)) }
        return parsed
            .filter { (_, t) -> t <= refTime }
            .maxByOrNull { (_, t) -> t }
            ?.first
            ?: segments.last()
    }

    private fun lookupIsfSegment(segments: List<IsfRatio>, refTime: LocalTime): Double {
        val match = lookupSegment(segments, refTime, { it.startTime }, "ISF")
        if (match.value <= 0.0) throw BusinessValidationException("ISF value must be positive")
        return match.value
    }

    private fun lookupIcrSegment(segments: List<IcrRatio>, refTime: LocalTime): Double {
        val match = lookupSegment(segments, refTime, { it.startTime }, "ICR")
        if (match.value <= 0.0) throw BusinessValidationException("ICR value must be positive")
        return match.value
    }

    private fun lookupTargetSegment(segments: List<GlucoseTarget>, refTime: LocalTime): Double {
        val match = lookupSegment(segments, refTime, { it.startTime }, "target")
        return (match.low + match.high) / 2.0
    }

    private fun parseSegmentTime(time: String): LocalTime =
        runCatching {
            val parts = time.split(":")
            LocalTime(parts[0].toInt(), parts[1].toInt())
        }.getOrElse {
            throw BusinessValidationException("Invalid segment time format: '$time' — expected HH:mm")
        }

    private fun trendAdjustment(trend: CgmTrend, isf: Double) = when (trend) {
        CgmTrend.DOUBLE_UP       ->  TREND_DOUBLE_MGDL_OFFSET / isf
        CgmTrend.SINGLE_UP       ->  TREND_SINGLE_MGDL_OFFSET / isf
        CgmTrend.FORTY_FIVE_UP   ->  TREND_FORTY_FIVE_MGDL_OFFSET / isf
        CgmTrend.FLAT            ->  0.0
        CgmTrend.FORTY_FIVE_DOWN -> -TREND_FORTY_FIVE_MGDL_OFFSET / isf
        CgmTrend.SINGLE_DOWN     -> -TREND_SINGLE_MGDL_OFFSET / isf
        CgmTrend.DOUBLE_DOWN     -> -TREND_DOUBLE_MGDL_OFFSET / isf
        CgmTrend.NONE            ->  0.0
    }

    private fun iobCoversFullCorrection(
        activeIob: Double,
        rawCorrection: Double,
        correctionDose: Double,
        bgMgDl: Double,
        target: Double,
    ) = activeIob > 0.0 && rawCorrection > 0.0 && correctionDose == 0.0 && bgMgDl > target

    private fun round2(v: Double) = Math.round(v * ROUND_TWO_DECIMAL_FACTOR) / ROUND_TWO_DECIMAL_FACTOR
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.domain.model

import kotlin.uuid.Uuid
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException

@Serializable
enum class ProfileStatus {
        ACTIVE,
        ARCHIVED,
        DRAFT,
        PROPOSED
}

/** Insulin-related settings for a profile. */
@Serializable
data class InsulinSettings(
        val insulinType: String,
        val durationOfAction: Int, // in minutes
)

/** Blood-glucose analysis range thresholds (both values required together). */
@Serializable
data class AnalysisRange(
        val low: Double,
        val high: Double,
)

/** All time-segmented schedule data for a profile. */
@Serializable
data class ProfileSchedule(
        val basal: List<BasalSegment>,
        val icr: List<IcrSegment>,
        val isf: List<IsfSegment>,
        val targets: List<TargetSegment>,
)

/** Doctor-patient collaboration metadata. */
@Serializable
data class ProfileCollaboration(
        val proposalReason: String? = null,
        val rejectionReason: String? = null,
)

@Serializable
data class Profile(
        val id: Uuid = Uuid.random(),
        val userId: Uuid,
        val previousProfileId: Uuid? = null,
        val name: String,
        val status: ProfileStatus,
        val createdAt: Instant = Clock.System.now(),
        val createdBy: Uuid? = null,
        val validFrom: Instant? = null,
        val activatedAt: Instant? = null,
        val archivedAt: Instant? = null,
        val settings: InsulinSettings,
        val analysisRange: AnalysisRange? = null,
        val schedule: ProfileSchedule,
        val collaboration: ProfileCollaboration? = null,
) {
    fun validate() {
        validatePhysics()
        validateBasalRequirements()
        validateTimeSegments()
        validateIcrRange()
        validateAnalysisThresholds()
    }

    private fun validateTimeSegments() {
        val sortedIcr = schedule.icr.sortedBy { it.startTime }
        if (sortedIcr.isNotEmpty() && sortedIcr.first().startTime != LocalTime(0, 0)) {
            throw BusinessValidationException("ICR profile must start at 00:00")
        }

        val sortedIsf = schedule.isf.sortedBy { it.startTime }
        if (sortedIsf.isNotEmpty() && sortedIsf.first().startTime != LocalTime(0, 0)) {
            throw BusinessValidationException("ISF profile must start at 00:00")
        }
    }

    private fun validatePhysics() {
        if (settings.durationOfAction < MIN_DURATION_OF_ACTION ||
            settings.durationOfAction > MAX_DURATION_OF_ACTION
        ) {
            throw BusinessValidationException(
                "Duration of action must be between $MIN_DURATION_OF_ACTION and " +
                    "$MAX_DURATION_OF_ACTION minutes (2-8 hours)"
            )
        }
        if (schedule.basal.any { it.value < 0 }) {
            throw BusinessValidationException("Basal values cannot be negative")
        }
        if (schedule.icr.any { it.value <= 0 }) {
            throw BusinessValidationException("ICR values must be positive")
        }
        if (schedule.isf.any { it.value <= 0 }) {
            throw BusinessValidationException("ISF values must be positive")
        }
        if (schedule.targets.any { it.low < 0 || it.high < 0 || it.low > it.high }) {
            throw BusinessValidationException("Target values are invalid or low exceeds high")
        }
    }

    private fun validateBasalRequirements() {
        if (schedule.basal.isEmpty()) {
            throw BusinessValidationException("Profile must have at least one basal segment")
        }

        val sortedBasal = schedule.basal.sortedBy { it.startTime }
        if (sortedBasal.firstOrNull()?.startTime != LocalTime(0, 0)) {
            throw BusinessValidationException("Basal profile must start at 00:00")
        }

        val distinctTimes = schedule.basal.map { it.startTime }.distinct()
        if (distinctTimes.size != schedule.basal.size) {
            throw BusinessValidationException("Basal segments cannot have overlapping start times")
        }

        var totalDailyBasal = 0.0
        for (i in sortedBasal.indices) {
            val current = sortedBasal[i]
            val nextTimeInSeconds = if (i < sortedBasal.size - 1) {
                sortedBasal[i + 1].startTime.toSecondOfDay()
            } else {
                SECONDS_IN_DAY
            }
            val durationInHours =
                (nextTimeInSeconds - current.startTime.toSecondOfDay()) / SECONDS_IN_HOUR
            totalDailyBasal += durationInHours * current.value
        }

        if (totalDailyBasal > MAX_DAILY_BASAL_U) {
            throw BusinessValidationException(
                "Total Daily Basal exceeds safe clinical limit ($MAX_DAILY_BASAL_U U/day)"
            )
        }
    }

    private fun validateAnalysisThresholds() {
        analysisRange?.let { range ->
            if (range.low < ANALYSIS_LOW_MIN || range.low > ANALYSIS_LOW_MAX) {
                throw BusinessValidationException(
                    "analysisLow must be between $ANALYSIS_LOW_MIN and $ANALYSIS_LOW_MAX mg/dL"
                )
            }
            if (range.high < ANALYSIS_HIGH_MIN || range.high > ANALYSIS_HIGH_MAX) {
                throw BusinessValidationException(
                    "analysisHigh must be between $ANALYSIS_HIGH_MIN and $ANALYSIS_HIGH_MAX mg/dL"
                )
            }
            if (range.low >= range.high) {
                throw BusinessValidationException(
                    "analysisLow (${range.low}) must be less than analysisHigh (${range.high})"
                )
            }
        }
    }

    private fun validateIcrRange() {
        if (schedule.icr.any { it.value < MIN_ICR || it.value > MAX_ICR }) {
            throw BusinessValidationException("ICR values must be between $MIN_ICR and $MAX_ICR g/U")
        }
    }

    companion object {
        const val SECONDS_IN_DAY = 86400
        const val SECONDS_IN_HOUR = 3600.0
        const val MAX_DAILY_BASAL_U = 150.0
        const val MIN_ICR = 1.0
        const val MAX_ICR = 50.0
        const val MIN_ISF_MGDL = 10.0
        const val MAX_ISF_MGDL = 200.0
        const val DEFAULT_DURATION_OF_ACTION = 240 // 4 hours in minutes (fallback for legacy API)
        const val MIN_DURATION_OF_ACTION = 120  // 2 hours in minutes
        const val MAX_DURATION_OF_ACTION = 480  // 8 hours in minutes
        const val ANALYSIS_LOW_MIN = 54.0   // mg/dL -- clinical very-low threshold floor
        const val ANALYSIS_LOW_MAX = 180.0  // mg/dL -- analysisLow may not exceed target high ceiling
        const val ANALYSIS_HIGH_MIN = 120.0 // mg/dL -- analysisHigh minimum sensible threshold
        const val ANALYSIS_HIGH_MAX = 400.0 // mg/dL -- clinical very-high threshold ceiling
    }
}

data class PagedProfiles(
    val items: List<Profile>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

interface TimeSegment {
        val startTime: LocalTime
}

@Serializable
data class BasalSegment(override val startTime: LocalTime, val value: Double) : TimeSegment

@Serializable
data class IcrSegment(override val startTime: LocalTime, val value: Double) : TimeSegment

@Serializable
data class IsfSegment(override val startTime: LocalTime, val value: Double) : TimeSegment

@Serializable
data class TargetSegment(override val startTime: LocalTime, val low: Double, val high: Double) :
        TimeSegment

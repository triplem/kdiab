package org.javafreedom.kdiab.profiles.domain.model

import kotlin.uuid.Uuid
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException

@Serializable
enum class ProfileStatus {
        ACTIVE,
        ARCHIVED,
        DRAFT,
        PROPOSED
}


@Serializable
data class Profile(
        val id: Uuid = Uuid.random(),
        val userId: Uuid,
        val previousProfileId: Uuid? = null,
        val name: String,
        val insulinType: String,
        val units: String = "mg/dL",
        val durationOfAction: Int, // in minutes
        val timeZone: TimeZone = TimeZone.UTC,
        val status: ProfileStatus,
        val createdAt: Instant = Clock.System.now(),
        val validFrom: Instant? = null,
        val activatedAt: Instant? = null,
        val archivedAt: Instant? = null,
        val proposalReason: String? = null,
        val createdBy: Uuid? = null,
        val rejectionReason: String? = null,
        val analysisLow: Double? = null,
        val analysisHigh: Double? = null,
        val carbAbsorptionRateGPerHour: Double? = null,
        val basal: List<BasalSegment>,
        val icr: List<IcrSegment>,
        val isf: List<IsfSegment>,
        val targets: List<TargetSegment>
) {
    fun validate() {
        validatePhysics()
        validateBasalRequirements()
        validateTimeSegments()
        validateUnitHeuristics()
    }

    private fun validateTimeSegments() {
        val sortedIcr = icr.sortedBy { it.startTime }
        if (sortedIcr.isNotEmpty() && sortedIcr.first().startTime != LocalTime(0, 0)) {
            throw BusinessValidationException("ICR profile must start at 00:00")
        }

        val sortedIsf = isf.sortedBy { it.startTime }
        if (sortedIsf.isNotEmpty() && sortedIsf.first().startTime != LocalTime(0, 0)) {
            throw BusinessValidationException("ISF profile must start at 00:00")
        }
    }

    private fun validatePhysics() {
        if (durationOfAction <= 0) {
            throw BusinessValidationException("Duration of action must be positive")
        }
        if (basal.any { it.value < 0 }) {
            throw BusinessValidationException("Basal values cannot be negative")
        }
        if (icr.any { it.value < 0 }) {
            throw BusinessValidationException("ICR values cannot be negative")
        }
        if (isf.any { it.value < 0 }) {
            throw BusinessValidationException("ISF values cannot be negative")
        }
        if (targets.any { it.low < 0 || it.high < 0 || it.low > it.high }) {
            throw BusinessValidationException("Target values are invalid or low exceeds high")
        }
        carbAbsorptionRateGPerHour?.let {
            if (it < MIN_CARB_ABSORPTION_RATE || it > MAX_CARB_ABSORPTION_RATE) {
                throw BusinessValidationException(
                    "Carb absorption rate must be between $MIN_CARB_ABSORPTION_RATE and $MAX_CARB_ABSORPTION_RATE g/hr"
                )
            }
        }
    }

    private fun validateBasalRequirements() {
        if (basal.isEmpty()) {
            throw BusinessValidationException("Profile must have at least one basal segment")
        }

        val sortedBasal = basal.sortedBy { it.startTime }
        if (sortedBasal.firstOrNull()?.startTime != LocalTime(0, 0)) {
             throw BusinessValidationException("Basal profile must start at 00:00")
        }

        val distinctTimes = basal.map { it.startTime }.distinct()
        if (distinctTimes.size != basal.size) {
             throw BusinessValidationException("Basal segments cannot have overlapping start times")
        }

        var totalDailyBasal = 0.0
        for (i in sortedBasal.indices) {
            val current = sortedBasal[i]
            val nextTimeInSeconds = if (i < sortedBasal.size - 1) {
                sortedBasal[i + 1].startTime.toSecondOfDay()
            } else {
                SECONDS_IN_DAY // 24:00:00 in seconds
            }
            val durationInHours = (nextTimeInSeconds - current.startTime.toSecondOfDay()) / SECONDS_IN_HOUR
            totalDailyBasal += durationInHours * current.value
        }

        if (totalDailyBasal > MAX_DAILY_BASAL_U) {
            throw BusinessValidationException(
                "Total Daily Basal exceeds safe clinical limit ($MAX_DAILY_BASAL_U U/day)"
            )
        }
    }

    private fun validateUnitHeuristics() {
        if (icr.any { it.value < MIN_ICR || it.value > MAX_ICR }) {
            throw BusinessValidationException("ICR values must be between $MIN_ICR and $MAX_ICR g/U")
        }

        val normalizedUnits = units.lowercase()
        if (normalizedUnits == "mmol/l") {
            if (isf.any { it.value > MMOL_UPPER_BOUND } ||
                targets.any { it.low > MMOL_UPPER_BOUND || it.high > MMOL_UPPER_BOUND }) {
                 throw BusinessValidationException("Value too high for mmol/L (suspected mg/dL)")
            }
        } else if (normalizedUnits == "mg/dl") {
            if (targets.any { it.low < MGDL_LOWER_BOUND || it.high < MGDL_LOWER_BOUND }) {
                 throw BusinessValidationException("Target value too low for mg/dL (suspected mmol/L)")
            }
            if (isf.any { it.value < MIN_ISF_MGDL || it.value > MAX_ISF_MGDL }) {
                 throw BusinessValidationException(
                     "ISF value for mg/dL must be between $MIN_ISF_MGDL and $MAX_ISF_MGDL"
                 )
            }
        }
    }

    companion object {
        const val SECONDS_IN_DAY = 86400
        const val SECONDS_IN_HOUR = 3600.0
        const val MAX_DAILY_BASAL_U = 150.0
        const val MIN_ICR = 1.0
        const val MAX_ICR = 50.0
        const val MMOL_UPPER_BOUND = 30.0
        const val MGDL_LOWER_BOUND = 20.0
        const val MIN_ISF_MGDL = 10.0
        const val MAX_ISF_MGDL = 200.0
        const val MIN_CARB_ABSORPTION_RATE = 1.0
        const val MAX_CARB_ABSORPTION_RATE = 100.0
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

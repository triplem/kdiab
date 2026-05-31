@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.adapters.inbound.web

import kotlin.uuid.Uuid
import kotlin.time.Clock
import kotlin.time.Instant
import org.javafreedom.kdiab.profiles.api.models.AnalysisRange as ApiAnalysisRange
import org.javafreedom.kdiab.profiles.api.models.BasalSegment
import org.javafreedom.kdiab.profiles.api.models.CreateProfileRequest
import org.javafreedom.kdiab.profiles.api.models.IcrSegment
import org.javafreedom.kdiab.profiles.api.models.InsulinSettings as ApiInsulinSettings
import org.javafreedom.kdiab.profiles.api.models.InsulinToMealIntervalSegment as ApiInsulinToMealIntervalSegment
import org.javafreedom.kdiab.profiles.api.models.IsfSegment
import org.javafreedom.kdiab.profiles.api.models.Profile
import org.javafreedom.kdiab.profiles.api.models.ProfileCollaboration as ApiProfileCollaboration
import org.javafreedom.kdiab.profiles.api.models.ProfileSchedule as ApiProfileSchedule
import org.javafreedom.kdiab.profiles.api.models.TargetSegment
import org.javafreedom.kdiab.profiles.domain.model.AnalysisRange
import org.javafreedom.kdiab.profiles.domain.model.InsulinSettings
import org.javafreedom.kdiab.profiles.domain.model.InsulinToMealIntervalSegment
import org.javafreedom.kdiab.profiles.domain.model.Profile.Companion.DEFAULT_DURATION_OF_ACTION
import org.javafreedom.kdiab.profiles.domain.model.ProfileCollaboration
import org.javafreedom.kdiab.profiles.domain.model.ProfileSchedule
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus
import org.javafreedom.kdiab.profiles.domain.model.Profile as DomainProfile

fun CreateProfileRequest.toDomain(
    userId: Uuid,
    status: ProfileStatus = ProfileStatus.DRAFT,
    createdBy: Uuid? = null
): DomainProfile {
    // Support both new nested and legacy flat fields
    val insulinType = this.settings?.insulinType ?: this.insulinType ?: ""
    val durationOfAction = this.settings?.durationOfAction ?: this.durationOfAction ?: DEFAULT_DURATION_OF_ACTION
    val analysisLowVal = this.analysisRange?.low ?: this.analysisLow
    val analysisHighVal = this.analysisRange?.high ?: this.analysisHigh
    val basalList = this.schedule?.basal ?: this.basal ?: emptyList()
    val icrList = this.schedule?.icr ?: this.icr ?: emptyList()
    val isfList = this.schedule?.isf ?: this.isf ?: emptyList()
    val targetsList = this.schedule?.targets ?: this.targets ?: emptyList()
    val seaList = this.schedule?.insulinToMealInterval ?: emptyList()
    val proposalReasonVal = this.collaboration?.proposalReason ?: this.proposalReason

    return DomainProfile(
        id = Uuid.random(),
        userId = userId,
        name = this.name,
        status = status,
        createdAt = Clock.System.now(),
        createdBy = createdBy,
        settings = InsulinSettings(
            insulinType = insulinType,
            durationOfAction = durationOfAction,
        ),
        analysisRange = if (analysisLowVal != null && analysisHighVal != null) {
            AnalysisRange(low = analysisLowVal, high = analysisHighVal)
        } else {
            null
        },
        schedule = ProfileSchedule(
            basal = basalList.map {
                org.javafreedom.kdiab.profiles.domain.model.BasalSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.value
                )
            },
            icr = icrList.map {
                org.javafreedom.kdiab.profiles.domain.model.IcrSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.value
                )
            },
            isf = isfList.map {
                org.javafreedom.kdiab.profiles.domain.model.IsfSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.value
                )
            },
            targets = targetsList.map {
                org.javafreedom.kdiab.profiles.domain.model.TargetSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.low,
                    it.high
                )
            },
            insulinToMealInterval = seaList.map {
                InsulinToMealIntervalSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.minutes
                )
            },
        ),
        collaboration = if (proposalReasonVal != null) {
            ProfileCollaboration(proposalReason = proposalReasonVal)
        } else {
            null
        },
    )
}

fun Profile.toDomain(): DomainProfile {
    // settings and schedule are required (non-null) in the generated API Profile;
    // legacy flat fields are deprecated fallbacks for older API consumers.
    val insulinType = this.settings.insulinType
    val durationOfAction = this.settings.durationOfAction
    val analysisLowVal = this.analysisRange?.low
    val analysisHighVal = this.analysisRange?.high
    val basalList = this.schedule.basal
    val icrList = this.schedule.icr
    val isfList = this.schedule.isf
    val targetsList = this.schedule.targets
    val seaList = this.schedule.insulinToMealInterval ?: emptyList()
    val proposalReasonVal = this.collaboration?.proposalReason ?: this.proposalReason
    val rejectionReasonVal = this.collaboration?.rejectionReason ?: this.rejectionReason

    return DomainProfile(
        id = Uuid.parse(this.id),
        userId = Uuid.parse(this.userId),
        name = this.name,
        status = ProfileStatus.valueOf(this.status.name),
        previousProfileId = this.previousProfileId?.let { Uuid.parse(it) },
        createdAt = this.createdAt?.let { Instant.parse(it) } ?: Clock.System.now(),
        createdBy = this.createdBy?.let { Uuid.parse(it) },
        settings = InsulinSettings(
            insulinType = insulinType,
            durationOfAction = durationOfAction,
        ),
        analysisRange = if (analysisLowVal != null && analysisHighVal != null) {
            AnalysisRange(low = analysisLowVal, high = analysisHighVal)
        } else {
            null
        },
        schedule = ProfileSchedule(
            basal = basalList.map {
                org.javafreedom.kdiab.profiles.domain.model.BasalSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.value
                )
            },
            icr = icrList.map {
                org.javafreedom.kdiab.profiles.domain.model.IcrSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.value
                )
            },
            isf = isfList.map {
                org.javafreedom.kdiab.profiles.domain.model.IsfSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.value
                )
            },
            targets = targetsList.map {
                org.javafreedom.kdiab.profiles.domain.model.TargetSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.low,
                    it.high
                )
            },
            insulinToMealInterval = seaList.map {
                InsulinToMealIntervalSegment(
                    kotlinx.datetime.LocalTime.parse(it.startTime),
                    it.minutes
                )
            },
        ),
        collaboration = if (proposalReasonVal != null || rejectionReasonVal != null) {
            ProfileCollaboration(
                proposalReason = proposalReasonVal,
                rejectionReason = rejectionReasonVal,
            )
        } else {
            null
        },
    )
}

fun DomainProfile.toApi(): Profile {
    return Profile(
        id = this.id.toString(),
        userId = this.userId.toString(),
        name = this.name,
        previousProfileId = this.previousProfileId?.toString(),
        status = Profile.Status.valueOf(this.status.name),
        createdAt = this.createdAt.toString(),
        createdBy = this.createdBy?.toString(),
        validFrom = this.validFrom?.toString(),
        activatedAt = this.activatedAt?.toString(),
        archivedAt = this.archivedAt?.toString(),
        // New nested fields
        settings = ApiInsulinSettings(
            insulinType = this.settings.insulinType,
            durationOfAction = this.settings.durationOfAction,
        ),
        analysisRange = this.analysisRange?.let {
            ApiAnalysisRange(low = it.low, high = it.high)
        },
        schedule = ApiProfileSchedule(
            basal = this.schedule.basal.map { BasalSegment(it.startTime.toString(), it.value) },
            icr = this.schedule.icr.map { IcrSegment(it.startTime.toString(), it.value) },
            isf = this.schedule.isf.map { IsfSegment(it.startTime.toString(), it.value) },
            targets = this.schedule.targets.map { TargetSegment(it.startTime.toString(), it.low, it.high) },
            insulinToMealInterval = this.schedule.insulinToMealInterval
                .map { ApiInsulinToMealIntervalSegment(it.startTime.toString(), it.minutes) }
                .takeIf { it.isNotEmpty() },
        ),
        collaboration = if (this.collaboration != null) {
            ApiProfileCollaboration(
                proposalReason = this.collaboration.proposalReason,
                rejectionReason = this.collaboration.rejectionReason,
            )
        } else {
            null
        },
        // Legacy flat fields (deprecated — preserved for backward compatibility)
        insulinType = this.settings.insulinType,
        durationOfAction = this.settings.durationOfAction,
        analysisLow = this.analysisRange?.low,
        analysisHigh = this.analysisRange?.high,
        proposalReason = this.collaboration?.proposalReason,
        rejectionReason = this.collaboration?.rejectionReason,
        basal = this.schedule.basal.map { BasalSegment(it.startTime.toString(), it.value) },
        icr = this.schedule.icr.map { IcrSegment(it.startTime.toString(), it.value) },
        isf = this.schedule.isf.map { IsfSegment(it.startTime.toString(), it.value) },
        targets = this.schedule.targets.map { TargetSegment(it.startTime.toString(), it.low, it.high) },
    )
}

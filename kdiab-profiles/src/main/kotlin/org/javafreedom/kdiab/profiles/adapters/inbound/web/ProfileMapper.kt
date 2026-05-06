@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.adapters.inbound.web

import kotlin.uuid.Uuid
import kotlin.time.Clock
import kotlin.time.Instant
import org.javafreedom.kdiab.profiles.api.models.BasalSegment
import org.javafreedom.kdiab.profiles.api.models.CreateProfileRequest
import org.javafreedom.kdiab.profiles.api.models.IcrSegment
import org.javafreedom.kdiab.profiles.api.models.IsfSegment
import org.javafreedom.kdiab.profiles.api.models.Profile
import org.javafreedom.kdiab.profiles.api.models.TargetSegment
import org.javafreedom.kdiab.profiles.domain.model.Profile as DomainProfile
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus

fun CreateProfileRequest.toDomain(userId: Uuid, status: ProfileStatus = ProfileStatus.DRAFT): DomainProfile {
    return DomainProfile(
        id = Uuid.random(),
        userId = userId,
        name = this.name,
        insulinType = this.insulinType,
        durationOfAction = this.durationOfAction,
        status = status,
        createdAt = Clock.System.now(),
        proposalReason = this.proposalReason,
        basal = this.basal?.map {
            org.javafreedom.kdiab.profiles.domain.model.BasalSegment(
                kotlinx.datetime.LocalTime.parse(it.startTime),
                it.value
            )
        } ?: emptyList(),
        icr = this.icr?.map {
            org.javafreedom.kdiab.profiles.domain.model.IcrSegment(
                kotlinx.datetime.LocalTime.parse(it.startTime),
                it.value
            )
        } ?: emptyList(),
        isf = this.isf?.map {
            org.javafreedom.kdiab.profiles.domain.model.IsfSegment(
                kotlinx.datetime.LocalTime.parse(it.startTime),
                it.value
            )
        } ?: emptyList(),
        targets = this.targets?.map {
            org.javafreedom.kdiab.profiles.domain.model.TargetSegment(
                kotlinx.datetime.LocalTime.parse(it.startTime),
                it.low,
                it.high
            )
        } ?: emptyList()
    )
}

fun Profile.toDomain(): DomainProfile {
    return DomainProfile(
        id = Uuid.parse(this.id),
        userId = Uuid.parse(this.userId),
        name = this.name,
        insulinType = this.insulinType,
        units = this.units?.value ?: "mg/dL",
        timeZone = kotlinx.datetime.TimeZone.UTC,
        durationOfAction = this.durationOfAction,
        status = ProfileStatus.valueOf(this.status.name),
        previousProfileId = this.previousProfileId?.let { Uuid.parse(it) },
        createdAt = this.createdAt?.let { Instant.parse(it) } ?: Clock.System.now(),
        basal = this.basal?.map {
            org.javafreedom.kdiab.profiles.domain.model.BasalSegment(
                kotlinx.datetime.LocalTime.parse(it.startTime),
                it.value
            )
        } ?: emptyList(),
        icr = this.icr?.map {
            org.javafreedom.kdiab.profiles.domain.model.IcrSegment(
                kotlinx.datetime.LocalTime.parse(it.startTime),
                it.value
            )
        } ?: emptyList(),
        isf = this.isf?.map {
            org.javafreedom.kdiab.profiles.domain.model.IsfSegment(
                kotlinx.datetime.LocalTime.parse(it.startTime),
                it.value
            )
        } ?: emptyList(),
        targets = this.targets?.map {
            org.javafreedom.kdiab.profiles.domain.model.TargetSegment(
                kotlinx.datetime.LocalTime.parse(it.startTime),
                it.low,
                it.high
            )
        } ?: emptyList()
    )
}

fun DomainProfile.toApi(): Profile {
    return Profile(
        id = this.id.toString(),
        userId = this.userId.toString(),
        name = this.name,
        previousProfileId = this.previousProfileId?.toString(),
        insulinType = this.insulinType,
        units = Profile.Units.entries.find { it.value == this.units } ?: Profile.Units.mgSlashDL,
        durationOfAction = this.durationOfAction,
        timeZone = this.timeZone.id,
        status = Profile.Status.valueOf(this.status.name),
        createdAt = this.createdAt.toString(),
        validFrom = this.validFrom?.toString(),
        activatedAt = this.activatedAt?.toString(),
        archivedAt = this.archivedAt?.toString(),
        proposalReason = this.proposalReason,
        basal = this.basal.map { BasalSegment(it.startTime.toString(), it.value) },
        icr = this.icr.map { IcrSegment(it.startTime.toString(), it.value) },
        isf = this.isf.map { IsfSegment(it.startTime.toString(), it.value) },
        targets = this.targets.map { TargetSegment(it.startTime.toString(), it.low, it.high) }
    )
}

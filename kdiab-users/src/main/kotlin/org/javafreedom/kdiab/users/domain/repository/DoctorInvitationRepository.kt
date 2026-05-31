@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.repository

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.users.domain.model.DoctorInvitation
import org.javafreedom.kdiab.users.domain.model.InvitationStatus

interface DoctorInvitationRepository {
    suspend fun save(invitation: DoctorInvitation): DoctorInvitation
    suspend fun findById(id: Uuid): DoctorInvitation?
    suspend fun findByDoctorId(
        doctorId: Uuid,
        statuses: Set<InvitationStatus> = emptySet(),
        limit: Int = 100,
        offset: Long = 0,
    ): List<DoctorInvitation>
    suspend fun countByDoctorId(
        doctorId: Uuid,
        statuses: Set<InvitationStatus> = emptySet(),
    ): Long
    suspend fun findPendingByPatientId(patientId: Uuid): List<DoctorInvitation>
    suspend fun updateStatus(id: Uuid, status: InvitationStatus, resolvedAt: Instant?): Boolean
    suspend fun expireBefore(cutoff: Instant): Int
    suspend fun existsPendingByDoctorAndPatient(doctorId: Uuid, patientId: Uuid): Boolean
}

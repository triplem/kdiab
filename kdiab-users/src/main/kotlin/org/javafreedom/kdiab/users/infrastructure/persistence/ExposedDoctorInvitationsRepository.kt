@file:Suppress("WildcardImport", "InjectDispatcher")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.javafreedom.kdiab.users.domain.model.DoctorInvitation
import org.javafreedom.kdiab.users.domain.model.InvitationStatus
import org.javafreedom.kdiab.users.domain.repository.DoctorInvitationRepository

object DoctorInvitationsTable : Table("doctor_invitations") {
    private const val ISO_INSTANT_LEN = 50
    private const val STATUS_LEN = 20
    private const val PATIENT_IDENTIFIER_LEN = 255

    val id = uuid("id")
    val doctorId = uuid("doctor_id")
    val patientIdentifier = varchar("patient_identifier", PATIENT_IDENTIFIER_LEN)
    val patientId = uuid("patient_id").nullable()
    val status = varchar("status", STATUS_LEN)
    val message = text("message").nullable()
    val createdAt = varchar("created_at", ISO_INSTANT_LEN)
    val expiresAt = varchar("expires_at", ISO_INSTANT_LEN)
    val resolvedAt = varchar("resolved_at", ISO_INSTANT_LEN).nullable()

    override val primaryKey = PrimaryKey(id)
}

class ExposedDoctorInvitationsRepository : DoctorInvitationRepository {

    override suspend fun save(invitation: DoctorInvitation): DoctorInvitation =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                DoctorInvitationsTable.insert {
                    it[id] = invitation.id
                    it[doctorId] = invitation.doctorId
                    it[patientIdentifier] = invitation.patientIdentifier
                    it[patientId] = invitation.patientId
                    it[status] = invitation.status.name
                    it[message] = invitation.message
                    it[createdAt] = invitation.createdAt.toString()
                    it[expiresAt] = invitation.expiresAt.toString()
                    it[resolvedAt] = invitation.resolvedAt?.toString()
                }
            }
            invitation
        }

    override suspend fun findById(id: Uuid): DoctorInvitation? =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                DoctorInvitationsTable.selectAll()
                    .where { DoctorInvitationsTable.id eq id }
                    .singleOrNull()
                    ?.toInvitation()
            }
        }

    override suspend fun findByDoctorId(
        doctorId: Uuid,
        statuses: Set<InvitationStatus>,
        limit: Int,
        offset: Long,
    ): List<DoctorInvitation> =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                val query = DoctorInvitationsTable.selectAll()
                    .where {
                        if (statuses.isEmpty()) {
                            DoctorInvitationsTable.doctorId eq doctorId
                        } else {
                            (DoctorInvitationsTable.doctorId eq doctorId) and
                                (DoctorInvitationsTable.status inList statuses.map { it.name })
                        }
                    }
                    .orderBy(DoctorInvitationsTable.createdAt to SortOrder.DESC)
                    .limit(limit)
                    .offset(offset)
                query.map { it.toInvitation() }
            }
        }

    override suspend fun findPendingByPatientId(patientId: Uuid): List<DoctorInvitation> =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                DoctorInvitationsTable.selectAll()
                    .where {
                        (DoctorInvitationsTable.patientId eq patientId) and
                            (DoctorInvitationsTable.status eq InvitationStatus.PENDING.name)
                    }
                    .orderBy(DoctorInvitationsTable.createdAt to SortOrder.DESC)
                    .map { it.toInvitation() }
            }
        }

    override suspend fun updateStatus(id: Uuid, status: InvitationStatus, resolvedAt: Instant?): Boolean =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                DoctorInvitationsTable.update({ DoctorInvitationsTable.id eq id }) {
                    it[DoctorInvitationsTable.status] = status.name
                    it[DoctorInvitationsTable.resolvedAt] = resolvedAt?.toString()
                }
            } > 0
        }

    override suspend fun expireBefore(cutoff: Instant): Int =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                DoctorInvitationsTable.update({
                    (DoctorInvitationsTable.status eq InvitationStatus.PENDING.name) and
                        (DoctorInvitationsTable.expiresAt lessEq cutoff.toString())
                }) {
                    it[status] = InvitationStatus.EXPIRED.name
                }
            }
        }

    override suspend fun existsPendingByDoctorAndPatient(doctorId: Uuid, patientId: Uuid): Boolean =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                DoctorInvitationsTable.selectAll()
                    .where {
                        (DoctorInvitationsTable.doctorId eq doctorId) and
                            (DoctorInvitationsTable.patientId eq patientId) and
                            (DoctorInvitationsTable.status eq InvitationStatus.PENDING.name)
                    }
                    .count() > 0
            }
        }

    private fun ResultRow.toInvitation() = DoctorInvitation(
        id = this[DoctorInvitationsTable.id],
        doctorId = this[DoctorInvitationsTable.doctorId],
        patientIdentifier = this[DoctorInvitationsTable.patientIdentifier],
        patientId = this[DoctorInvitationsTable.patientId],
        status = InvitationStatus.valueOf(this[DoctorInvitationsTable.status]),
        message = this[DoctorInvitationsTable.message],
        createdAt = this[DoctorInvitationsTable.createdAt].parseInstant(),
        expiresAt = this[DoctorInvitationsTable.expiresAt].parseInstant(),
        resolvedAt = this[DoctorInvitationsTable.resolvedAt]?.parseInstant(),
    )
}

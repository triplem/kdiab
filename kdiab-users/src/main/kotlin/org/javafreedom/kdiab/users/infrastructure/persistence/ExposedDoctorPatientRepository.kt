@file:Suppress("WildcardImport", "InjectDispatcher")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository

object DoctorPatientTable : Table("doctor_patient") {
    private const val ISO_INSTANT_LEN = 50

    val doctorId = uuid("doctor_id")
    val patientId = uuid("patient_id")
    val createdAt = varchar("created_at", ISO_INSTANT_LEN)

    override val primaryKey = PrimaryKey(doctorId, patientId)
}

class ExposedDoctorPatientRepository : DoctorPatientRepository {

    override suspend fun findByDoctorId(doctorId: Uuid, limit: Int, offset: Long): List<DoctorPatientRelation> =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                DoctorPatientTable.selectAll()
                    .where { DoctorPatientTable.doctorId eq doctorId }
                    .orderBy(DoctorPatientTable.createdAt to SortOrder.ASC)
                    .limit(limit).offset(offset)
                    .map { row ->
                        DoctorPatientRelation(
                            doctorId = doctorId,
                            patientId = row[DoctorPatientTable.patientId],
                            createdAt = row[DoctorPatientTable.createdAt].parseInstant(),
                        )
                    }
            }
        }

    override suspend fun findAllPatientIdsByDoctorId(doctorId: Uuid): List<Uuid> =
        findByDoctorId(doctorId).map { it.patientId }

    override suspend fun save(relation: DoctorPatientRelation): DoctorPatientRelation =
        withContext(Dispatchers.IO) {
            try {
                suspendTransaction {
                    DoctorPatientTable.insert {
                        it[doctorId] = relation.doctorId
                        it[patientId] = relation.patientId
                        it[createdAt] = relation.createdAt.toString()
                    }
                }
            } catch (e: ExposedSQLException) {
                throw ConflictException("Doctor-patient relation already exists", e)
            }
            relation
        }

    override suspend fun delete(doctorId: Uuid, patientId: Uuid): Boolean =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                DoctorPatientTable.deleteWhere {
                    (DoctorPatientTable.doctorId eq doctorId) and
                    (DoctorPatientTable.patientId eq patientId)
                }
            } > 0
        }

    override suspend fun deleteByUserId(userId: Uuid): Unit =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                DoctorPatientTable.deleteWhere {
                    (DoctorPatientTable.doctorId eq userId) or
                    (DoctorPatientTable.patientId eq userId)
                }
            }
        }
}

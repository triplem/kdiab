@file:Suppress("WildcardImport", "InjectDispatcher")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository

object DoctorPatientTable : Table("doctor_patient") {
    private const val UUID_LEN = 36
    private const val ISO_INSTANT_LEN = 50

    val doctorId = varchar("doctor_id", UUID_LEN)
    val patientId = varchar("patient_id", UUID_LEN)
    val createdAt = varchar("created_at", ISO_INSTANT_LEN)

    override val primaryKey = PrimaryKey(doctorId, patientId)
}

class ExposedDoctorPatientRepository : DoctorPatientRepository {

    override suspend fun findByDoctorId(doctorId: Uuid, limit: Int, offset: Long): List<DoctorPatientRelation> =
        withContext(Dispatchers.IO) {
            transaction {
                DoctorPatientTable.selectAll()
                    .where { DoctorPatientTable.doctorId eq doctorId.toString() }
                    .orderBy(DoctorPatientTable.createdAt to SortOrder.ASC)
                    .limit(limit).offset(offset)
                    .map { row ->
                        DoctorPatientRelation(
                            doctorId = doctorId,
                            patientId = Uuid.parse(row[DoctorPatientTable.patientId]),
                            createdAt = Instant.parse(row[DoctorPatientTable.createdAt]),
                        )
                    }
            }
        }

    override suspend fun findAllPatientIdsByDoctorId(doctorId: Uuid): List<Uuid> =
        findByDoctorId(doctorId).map { it.patientId }

    override suspend fun save(relation: DoctorPatientRelation): DoctorPatientRelation =
        withContext(Dispatchers.IO) {
            try {
                transaction {
                    DoctorPatientTable.insert {
                        it[doctorId] = relation.doctorId.toString()
                        it[patientId] = relation.patientId.toString()
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
            transaction {
                DoctorPatientTable.deleteWhere {
                    (DoctorPatientTable.doctorId eq doctorId.toString()) and
                    (DoctorPatientTable.patientId eq patientId.toString())
                }
            } > 0
        }

    override suspend fun deleteByUserId(userId: Uuid): Unit =
        withContext(Dispatchers.IO) {
            transaction {
                val id = userId.toString()
                DoctorPatientTable.deleteWhere {
                    (DoctorPatientTable.doctorId eq id) or
                    (DoctorPatientTable.patientId eq id)
                }
            }
        }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.repository

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation

interface DoctorPatientRepository {
    suspend fun findByDoctorId(doctorId: Uuid): List<DoctorPatientRelation>
    suspend fun findAllPatientIdsByDoctorId(doctorId: Uuid): List<Uuid>
    suspend fun save(relation: DoctorPatientRelation): DoctorPatientRelation
    suspend fun delete(doctorId: Uuid, patientId: Uuid)
    suspend fun deleteByUserId(userId: Uuid)
}

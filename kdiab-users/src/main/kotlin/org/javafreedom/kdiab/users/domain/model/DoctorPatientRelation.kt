@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class DoctorPatientRelation(
    val doctorId: Uuid,
    val patientId: Uuid,
    val createdAt: Instant,
)

@file:Suppress("WildcardImport", "MagicNumber", "MaxLineLength", "TooManyFunctions")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.infrastructure.persistence

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentStatus
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType
import org.javafreedom.kdiab.treatments.domain.repository.TreatmentRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object TreatmentsTable : Table("treatments") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val treatedAt = timestamp("treated_at")
    val createdAt = timestamp("created_at")
    val type = varchar("type", 50)
    val data = jsonb<JsonObject>("data", Json.Default)
    val notes = text("notes").nullable()
    val status = varchar("status", 50).default("ACTIVE")

    override val primaryKey = PrimaryKey(id)
}

class ExposedTreatmentRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TreatmentRepository {

    override suspend fun save(treatment: Treatment): Treatment = withContext(ioDispatcher) {
        suspendTransaction {
            TreatmentsTable.insert {
                it[TreatmentsTable.id] = treatment.id
                it[TreatmentsTable.userId] = treatment.userId
                it[TreatmentsTable.treatedAt] = java.time.Instant.ofEpochMilli(treatment.treatedAt.toEpochMilliseconds())
                it[TreatmentsTable.createdAt] = java.time.Instant.ofEpochMilli(treatment.createdAt.toEpochMilliseconds())
                it[TreatmentsTable.type] = treatment.type.name
                it[TreatmentsTable.data] = treatment.data
                it[TreatmentsTable.notes] = treatment.notes
                it[TreatmentsTable.status] = treatment.status.name
            }
            treatment
        }
    }

    override suspend fun findByUserId(
        userId: Uuid,
        from: Instant?,
        to: Instant?,
        status: TreatmentStatus,
    ): List<Treatment> =
        withContext(ioDispatcher) {
            suspendTransaction {
                TreatmentsTable.selectAll()
                    .where {
                        var condition = (TreatmentsTable.userId eq userId) and
                            (TreatmentsTable.status eq status.name)
                        if (from != null) {
                            condition = condition and (TreatmentsTable.treatedAt greaterEq
                                java.time.Instant.ofEpochMilli(from.toEpochMilliseconds()))
                        }
                        if (to != null) {
                            condition = condition and (TreatmentsTable.treatedAt lessEq
                                java.time.Instant.ofEpochMilli(to.toEpochMilliseconds()))
                        }
                        condition
                    }
                    .orderBy(TreatmentsTable.treatedAt, SortOrder.DESC)
                    .map { it.toTreatment() }
            }
        }

    override suspend fun findByUserIdAndType(
        userId: Uuid,
        type: TreatmentType,
        from: Instant?,
        to: Instant?,
        status: TreatmentStatus,
    ): List<Treatment> =
        withContext(ioDispatcher) {
            suspendTransaction {
                TreatmentsTable.selectAll()
                    .where {
                        var condition = (TreatmentsTable.userId eq userId) and
                            (TreatmentsTable.type eq type.name) and
                            (TreatmentsTable.status eq status.name)
                        if (from != null) condition = condition and (TreatmentsTable.treatedAt greaterEq
                            java.time.Instant.ofEpochMilli(from.toEpochMilliseconds()))
                        if (to != null) condition = condition and (TreatmentsTable.treatedAt lessEq
                            java.time.Instant.ofEpochMilli(to.toEpochMilliseconds()))
                        condition
                    }
                    .orderBy(TreatmentsTable.treatedAt, SortOrder.DESC)
                    .map { it.toTreatment() }
            }
        }

    override suspend fun archiveAll(ids: List<Uuid>, userId: Uuid): Unit = withContext(ioDispatcher) {
        suspendTransaction {
            TreatmentsTable.update({
                (TreatmentsTable.id inList ids) and (TreatmentsTable.userId eq userId)
            }) {
                it[TreatmentsTable.status] = TreatmentStatus.ARCHIVED.name
            }
        }
    }

    override suspend fun deleteAll(ids: List<Uuid>, userId: Uuid): Unit = withContext(ioDispatcher) {
        suspendTransaction {
            TreatmentsTable.deleteWhere {
                (TreatmentsTable.id inList ids) and (TreatmentsTable.userId eq userId)
            }
        }
    }

    private fun ResultRow.toTreatment(): Treatment = Treatment(
        id = this[TreatmentsTable.id],
        userId = this[TreatmentsTable.userId],
        treatedAt = Instant.fromEpochMilliseconds(this[TreatmentsTable.treatedAt].toEpochMilli()),
        createdAt = Instant.fromEpochMilliseconds(this[TreatmentsTable.createdAt].toEpochMilli()),
        type = TreatmentType.valueOf(this[TreatmentsTable.type]),
        data = this[TreatmentsTable.data],
        notes = this[TreatmentsTable.notes],
        status = TreatmentStatus.valueOf(this[TreatmentsTable.status]),
    )
}

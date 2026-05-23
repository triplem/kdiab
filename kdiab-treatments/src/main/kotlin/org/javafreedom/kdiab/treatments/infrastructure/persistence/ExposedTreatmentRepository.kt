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
import org.javafreedom.kdiab.common.domain.SQL_UNIQUE_VIOLATION
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentStatus
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType
import org.javafreedom.kdiab.treatments.domain.repository.TreatmentRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.datetime.timestamp
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
        try {
            suspendTransaction {
                TreatmentsTable.insert {
                    it[TreatmentsTable.id] = treatment.id
                    it[TreatmentsTable.userId] = treatment.userId
                    it[TreatmentsTable.treatedAt] = treatment.treatedAt
                    it[TreatmentsTable.createdAt] = treatment.createdAt
                    it[TreatmentsTable.type] = treatment.type.name
                    it[TreatmentsTable.data] = treatment.data
                    it[TreatmentsTable.notes] = treatment.notes
                    it[TreatmentsTable.status] = treatment.status.name
                }
                treatment
            }
        } catch (ex: ExposedSQLException) {
            val sqlState = ex.cause?.let { (it as? java.sql.SQLException)?.sqlState }
            if (sqlState == SQL_UNIQUE_VIOLATION) {
                throw ConflictException("Treatment already exists: ${treatment.id}", ex)
            }
            throw ex
        }
    }

    override suspend fun findByUserId(
        userId: Uuid,
        from: Instant?,
        to: Instant?,
        status: TreatmentStatus,
        page: Int,
        size: Int,
    ): List<Treatment> =
        withContext(ioDispatcher) {
            suspendTransaction {
                TreatmentsTable.selectAll()
                    .where {
                        var condition = (TreatmentsTable.userId eq userId) and
                            (TreatmentsTable.status eq status.name) and
                            (TreatmentsTable.type neq TreatmentType.DEVICE_STATUS.name)
                        if (from != null) {
                            condition = condition and (TreatmentsTable.treatedAt greaterEq from)
                        }
                        if (to != null) {
                            condition = condition and (TreatmentsTable.treatedAt lessEq to)
                        }
                        condition
                    }
                    .orderBy(TreatmentsTable.treatedAt, SortOrder.DESC)
                    .limit(size)
                    .offset(page.toLong() * size)
                    .map { it.toTreatment() }
            }
        }

    override suspend fun countByUserId(
        userId: Uuid,
        from: Instant?,
        to: Instant?,
        status: TreatmentStatus,
    ): Long = withContext(ioDispatcher) {
        suspendTransaction {
            TreatmentsTable.selectAll()
                .where {
                    var condition = (TreatmentsTable.userId eq userId) and
                        (TreatmentsTable.status eq status.name) and
                        (TreatmentsTable.type neq TreatmentType.DEVICE_STATUS.name)
                    if (from != null) {
                        condition = condition and (TreatmentsTable.treatedAt greaterEq from)
                    }
                    if (to != null) {
                        condition = condition and (TreatmentsTable.treatedAt lessEq to)
                    }
                    condition
                }
                .count()
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
                        if (from != null) condition = condition and (TreatmentsTable.treatedAt greaterEq from)
                        if (to != null) condition = condition and (TreatmentsTable.treatedAt lessEq to)
                        condition
                    }
                    .orderBy(TreatmentsTable.treatedAt, SortOrder.DESC)
                    .map { it.toTreatment() }
            }
        }

    override suspend fun update(
        treatmentId: Uuid,
        userId: Uuid,
        treatedAt: Instant,
        data: JsonObject,
        notes: String?,
    ): Treatment = withContext(ioDispatcher) {
        suspendTransaction {
            TreatmentsTable.update({
                (TreatmentsTable.id eq treatmentId) and (TreatmentsTable.userId eq userId)
            }) {
                it[TreatmentsTable.treatedAt] = treatedAt
                it[TreatmentsTable.data] = data
                it[TreatmentsTable.notes] = notes
            }
            TreatmentsTable.selectAll()
                .where { (TreatmentsTable.id eq treatmentId) and (TreatmentsTable.userId eq userId) }
                .single()
                .toTreatment()
        }
    }

    override suspend fun findLatestTimestampByType(userId: Uuid, type: TreatmentType): Instant? =
        withContext(ioDispatcher) {
            suspendTransaction {
                TreatmentsTable.selectAll()
                    .where {
                        (TreatmentsTable.userId eq userId) and
                            (TreatmentsTable.type eq type.name) and
                            (TreatmentsTable.status eq TreatmentStatus.ACTIVE.name)
                    }
                    .orderBy(TreatmentsTable.treatedAt, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                    ?.let { it[TreatmentsTable.treatedAt] }
            }
        }

    override suspend fun findLatestTimestampsByTypes(
        userId: Uuid,
        types: Set<TreatmentType>,
    ): Map<TreatmentType, Instant> {
        if (types.isEmpty()) return emptyMap()
        return withContext(ioDispatcher) {
            suspendTransaction {
                val maxTreatedAt = TreatmentsTable.treatedAt.max()
                TreatmentsTable
                    .select(TreatmentsTable.type, maxTreatedAt)
                    .where {
                        (TreatmentsTable.userId eq userId) and
                            (TreatmentsTable.type inList types.map { it.name }) and
                            (TreatmentsTable.status eq TreatmentStatus.ACTIVE.name)
                    }
                    .groupBy(TreatmentsTable.type)
                    .mapNotNull { row ->
                        val type = TreatmentType.valueOf(row[TreatmentsTable.type])
                        row[maxTreatedAt]?.let { type to it }
                    }
                    .toMap()
            }
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

    override suspend fun unarchiveAll(ids: List<Uuid>, userId: Uuid): Unit = withContext(ioDispatcher) {
        suspendTransaction {
            TreatmentsTable.update({
                (TreatmentsTable.id inList ids) and (TreatmentsTable.userId eq userId)
            }) {
                it[TreatmentsTable.status] = TreatmentStatus.ACTIVE.name
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
        treatedAt = this[TreatmentsTable.treatedAt],
        createdAt = this[TreatmentsTable.createdAt],
        type = TreatmentType.valueOf(this[TreatmentsTable.type]),
        data = this[TreatmentsTable.data],
        notes = this[TreatmentsTable.notes],
        status = TreatmentStatus.valueOf(this[TreatmentsTable.status]),
    )
}

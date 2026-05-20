@file:Suppress("WildcardImport", "MagicNumber", "MaxLineLength", "TooManyFunctions")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.infrastructure.persistence

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.common.domain.SQL_UNIQUE_VIOLATION
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.measures.domain.model.Measure
import org.javafreedom.kdiab.measures.domain.model.MeasureSource
import org.javafreedom.kdiab.measures.domain.model.MeasureStatus
import org.javafreedom.kdiab.measures.domain.model.MeasureType
import org.javafreedom.kdiab.measures.domain.repository.MeasureRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object MeasuresTable : Table("measures") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val measuredAt = timestamp("measured_at")
    val createdAt = timestamp("created_at")
    val type = varchar("type", 50)
    val sourceField = varchar("source", 50)
    val data = jsonb<JsonObject>("data", Json.Default)
    val status = varchar("status", 50).default("ACTIVE")

    override val primaryKey = PrimaryKey(id)
}

class ExposedMeasureRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MeasureRepository {

    override suspend fun save(measure: Measure): Measure = withContext(ioDispatcher) {
        try {
            suspendTransaction {
                MeasuresTable.insert {
                    it[MeasuresTable.id] = measure.id
                    it[MeasuresTable.userId] = measure.userId
                    it[MeasuresTable.measuredAt] = java.time.Instant.ofEpochMilli(measure.measuredAt.toEpochMilliseconds())
                    it[MeasuresTable.createdAt] = java.time.Instant.ofEpochMilli(measure.createdAt.toEpochMilliseconds())
                    it[MeasuresTable.type] = measure.type.name
                    it[MeasuresTable.sourceField] = measure.source.name
                    it[MeasuresTable.data] = measure.data
                    it[MeasuresTable.status] = measure.status.name
                }
                measure
            }
        } catch (ex: ExposedSQLException) {
            val sqlState = ex.cause?.let { (it as? java.sql.SQLException)?.sqlState }
            if (sqlState == SQL_UNIQUE_VIOLATION) {
                throw ConflictException("Measure already exists: ${measure.id}", ex)
            }
            throw ex
        }
    }

    override suspend fun findByUserId(
        userId: Uuid, page: Int, size: Int, from: Instant?, to: Instant?,
        status: MeasureStatus,
    ): List<Measure> =
        withContext(ioDispatcher) {
            suspendTransaction {
                MeasuresTable.selectAll()
                    .where {
                        var condition = (MeasuresTable.userId eq userId) and
                            (MeasuresTable.status eq status.name)
                        if (from != null) {
                            condition = condition and (MeasuresTable.measuredAt greaterEq
                                java.time.Instant.ofEpochMilli(from.toEpochMilliseconds()))
                        }
                        if (to != null) {
                            condition = condition and (MeasuresTable.measuredAt lessEq
                                java.time.Instant.ofEpochMilli(to.toEpochMilliseconds()))
                        }
                        condition
                    }
                    .orderBy(MeasuresTable.measuredAt, SortOrder.DESC)
                    .limit(size)
                    .offset(page.toLong() * size)
                    .map { it.toMeasure() }
            }
        }

    override suspend fun countByUserId(userId: Uuid, from: Instant?, to: Instant?, status: MeasureStatus): Long =
        withContext(ioDispatcher) {
            suspendTransaction {
                MeasuresTable.selectAll()
                    .where {
                        var condition = (MeasuresTable.userId eq userId) and
                            (MeasuresTable.status eq status.name)
                        if (from != null) {
                            condition = condition and (MeasuresTable.measuredAt greaterEq
                                java.time.Instant.ofEpochMilli(from.toEpochMilliseconds()))
                        }
                        if (to != null) {
                            condition = condition and (MeasuresTable.measuredAt lessEq
                                java.time.Instant.ofEpochMilli(to.toEpochMilliseconds()))
                        }
                        condition
                    }
                    .count()
            }
        }

    override suspend fun findByUserIdAndType(userId: Uuid, type: MeasureType): List<Measure> =
        withContext(ioDispatcher) {
            suspendTransaction {
                MeasuresTable.selectAll()
                    .where { (MeasuresTable.userId eq userId) and (MeasuresTable.type eq type.name) }
                    .orderBy(MeasuresTable.measuredAt, SortOrder.DESC)
                    .map { it.toMeasure() }
            }
        }

    override suspend fun update(measureId: Uuid, userId: Uuid, measuredAt: Instant, data: JsonObject): Measure =
        withContext(ioDispatcher) {
            suspendTransaction {
                MeasuresTable.update({
                    (MeasuresTable.id eq measureId) and (MeasuresTable.userId eq userId)
                }) {
                    it[MeasuresTable.measuredAt] = java.time.Instant.ofEpochMilli(measuredAt.toEpochMilliseconds())
                    it[MeasuresTable.data] = data
                }
                MeasuresTable.selectAll()
                    .where { (MeasuresTable.id eq measureId) and (MeasuresTable.userId eq userId) }
                    .single()
                    .toMeasure()
            }
        }

    override suspend fun archive(ids: List<Uuid>, userId: Uuid): Unit = withContext(ioDispatcher) {
        suspendTransaction {
            MeasuresTable.update({
                (MeasuresTable.id inList ids) and (MeasuresTable.userId eq userId)
            }) {
                it[MeasuresTable.status] = MeasureStatus.ARCHIVED.name
            }
        }
    }

    override suspend fun unarchive(ids: List<Uuid>, userId: Uuid): Unit = withContext(ioDispatcher) {
        suspendTransaction {
            MeasuresTable.update({
                (MeasuresTable.id inList ids) and (MeasuresTable.userId eq userId)
            }) {
                it[MeasuresTable.status] = MeasureStatus.ACTIVE.name
            }
        }
    }

    override suspend fun deleteAll(ids: List<Uuid>, userId: Uuid): Unit = withContext(ioDispatcher) {
        suspendTransaction {
            MeasuresTable.deleteWhere {
                (MeasuresTable.id inList ids) and (MeasuresTable.userId eq userId)
            }
        }
    }

    private fun ResultRow.toMeasure(): Measure = Measure(
        id = this[MeasuresTable.id],
        userId = this[MeasuresTable.userId],
        measuredAt = Instant.fromEpochMilliseconds(this[MeasuresTable.measuredAt].toEpochMilli()),
        createdAt = Instant.fromEpochMilliseconds(this[MeasuresTable.createdAt].toEpochMilli()),
        type = MeasureType.valueOf(this[MeasuresTable.type]),
        source = MeasureSource.valueOf(this[MeasuresTable.sourceField]),
        data = this[MeasuresTable.data],
        status = MeasureStatus.valueOf(this[MeasuresTable.status])
    )
}

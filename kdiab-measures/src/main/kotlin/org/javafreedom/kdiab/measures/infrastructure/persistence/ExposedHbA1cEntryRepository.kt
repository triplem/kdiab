@file:Suppress("WildcardImport")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.infrastructure.persistence

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.javafreedom.kdiab.measures.domain.model.HbA1cEntry
import org.javafreedom.kdiab.measures.domain.model.HbA1cSource
import org.javafreedom.kdiab.measures.domain.repository.HbA1cEntryRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.javatime.timestamp

private const val VALUE_PERCENT_PRECISION = 5
private const val VALUE_PERCENT_SCALE = 2
private const val SOURCE_MAX_LENGTH = 20

object HbA1cEntriesTable : Table("hba1c_entries") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val measuredAt = timestamp("measured_at")
    val valuePercent = decimal("value_percent", precision = VALUE_PERCENT_PRECISION, scale = VALUE_PERCENT_SCALE)
    val sourceField = varchar("source", SOURCE_MAX_LENGTH).default("LAB")
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

class ExposedHbA1cEntryRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HbA1cEntryRepository {

    override suspend fun save(entry: HbA1cEntry): HbA1cEntry = withContext(ioDispatcher) {
        suspendTransaction {
            HbA1cEntriesTable.insert {
                it[HbA1cEntriesTable.id] = entry.id
                it[HbA1cEntriesTable.userId] = entry.userId
                it[HbA1cEntriesTable.measuredAt] =
                    java.time.Instant.ofEpochMilli(entry.measuredAt.toEpochMilliseconds())
                it[HbA1cEntriesTable.valuePercent] = entry.valuePercent.toBigDecimal()
                it[HbA1cEntriesTable.sourceField] = entry.source.name
                it[HbA1cEntriesTable.notes] = entry.notes
                it[HbA1cEntriesTable.createdAt] =
                    java.time.Instant.ofEpochMilli(entry.createdAt.toEpochMilliseconds())
            }
            entry
        }
    }

    override suspend fun findByUserIdBetween(
        userId: Uuid,
        from: Instant?,
        to: Instant?,
    ): List<HbA1cEntry> = withContext(ioDispatcher) {
        suspendTransaction {
            HbA1cEntriesTable.selectAll()
                .where {
                    var condition = (HbA1cEntriesTable.userId eq userId)
                    if (from != null) {
                        condition = condition and (HbA1cEntriesTable.measuredAt greaterEq
                            java.time.Instant.ofEpochMilli(from.toEpochMilliseconds()))
                    }
                    if (to != null) {
                        condition = condition and (HbA1cEntriesTable.measuredAt lessEq
                            java.time.Instant.ofEpochMilli(to.toEpochMilliseconds()))
                    }
                    condition
                }
                .orderBy(HbA1cEntriesTable.measuredAt, SortOrder.DESC)
                .map { it.toHbA1cEntry() }
        }
    }

    private fun ResultRow.toHbA1cEntry(): HbA1cEntry = HbA1cEntry(
        id = this[HbA1cEntriesTable.id],
        userId = this[HbA1cEntriesTable.userId],
        measuredAt = Instant.fromEpochMilliseconds(this[HbA1cEntriesTable.measuredAt].toEpochMilli()),
        valuePercent = this[HbA1cEntriesTable.valuePercent].toDouble(),
        source = HbA1cSource.valueOf(this[HbA1cEntriesTable.sourceField]),
        notes = this[HbA1cEntriesTable.notes],
        createdAt = Instant.fromEpochMilliseconds(this[HbA1cEntriesTable.createdAt].toEpochMilli()),
    )
}

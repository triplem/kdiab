@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.infrastructure.persistence

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry
import org.javafreedom.kdiab.carbs.domain.model.FoodEntryStatus
import org.javafreedom.kdiab.carbs.domain.repository.FoodEntryRepository
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.datetime.timestamp

// NAME_MAX_LENGTH and STATUS_MAX_LENGTH define the DB column widths for food_entries.
private const val NAME_MAX_LENGTH = 200
private const val STATUS_MAX_LENGTH = 20

// Wildcard imports are required: Exposed's query DSL (eq, and, lowerCase, like, selectAll, insert,
// update, deleteWhere, etc.) is spread across multiple extension functions in exposed-core and
// exposed-jdbc packages and cannot be imported individually without excessive boilerplate.

object FoodEntriesTable : Table("food_entries") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val name = varchar("name", NAME_MAX_LENGTH)
    val portionGrams = double("portion_grams")
    val carbsPer100g = double("carbs_per_100g")
    val status = varchar("status", STATUS_MAX_LENGTH).default("ACTIVE")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

class ExposedFoodEntryRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FoodEntryRepository {

    override suspend fun findByUserId(
        userId: Uuid,
        page: Int,
        size: Int,
        nameFilter: String?,
    ): List<FoodEntry> = withContext(ioDispatcher) {
        suspendTransaction {
            FoodEntriesTable.selectAll()
                .where {
                    var condition: Op<Boolean> =
                        (FoodEntriesTable.userId eq userId) and
                        (FoodEntriesTable.status eq FoodEntryStatus.ACTIVE.name)
                    if (!nameFilter.isNullOrBlank()) {
                        condition = condition and
                            (FoodEntriesTable.name.lowerCase() like "%${nameFilter.lowercase()}%")
                    }
                    condition
                }
                .orderBy(FoodEntriesTable.name, SortOrder.ASC)
                .limit(size)
                .offset(page.toLong() * size)
                .map { it.toFoodEntry() }
        }
    }

    override suspend fun countByUserId(userId: Uuid, nameFilter: String?): Long =
        withContext(ioDispatcher) {
            suspendTransaction {
                FoodEntriesTable.selectAll()
                    .where {
                        var condition: Op<Boolean> =
                            (FoodEntriesTable.userId eq userId) and
                            (FoodEntriesTable.status eq FoodEntryStatus.ACTIVE.name)
                        if (!nameFilter.isNullOrBlank()) {
                            condition = condition and
                                (FoodEntriesTable.name.lowerCase() like "%${nameFilter.lowercase()}%")
                        }
                        condition
                    }
                    .count()
            }
        }

    override suspend fun findById(id: Uuid, userId: Uuid): FoodEntry? = withContext(ioDispatcher) {
        suspendTransaction {
            FoodEntriesTable.selectAll()
                .where {
                    (FoodEntriesTable.id eq id) and
                    (FoodEntriesTable.userId eq userId)
                }
                .singleOrNull()
                ?.toFoodEntry()
        }
    }

    override suspend fun save(entry: FoodEntry): FoodEntry = withContext(ioDispatcher) {
        try {
            suspendTransaction {
                FoodEntriesTable.insert {
                    it[FoodEntriesTable.id] = entry.id
                    it[FoodEntriesTable.userId] = entry.userId
                    it[FoodEntriesTable.name] = entry.name
                    it[FoodEntriesTable.portionGrams] = entry.portionGrams
                    it[FoodEntriesTable.carbsPer100g] = entry.carbsPer100g
                    it[FoodEntriesTable.status] = entry.status.name
                    it[FoodEntriesTable.createdAt] = entry.createdAt
                    it[FoodEntriesTable.updatedAt] = entry.updatedAt
                }
                entry
            }
        } catch (ex: ExposedSQLException) {
            // SQL state 23505 = unique_violation; wrap in domain exception so callers
            // only need to handle domain exceptions, not infrastructure-level SQL errors.
            val sqlState = ex.cause?.let { (it as? java.sql.SQLException)?.sqlState }
            if (sqlState == "23505") {
                throw ConflictException("Food entry already exists: ${entry.id}", ex)
            }
            throw ex
        }
    }

    override suspend fun archive(id: Uuid, userId: Uuid): FoodEntry = withContext(ioDispatcher) {
        suspendTransaction {
            val now = Clock.System.now()
            FoodEntriesTable.update({
                (FoodEntriesTable.id eq id) and
                (FoodEntriesTable.userId eq userId)
            }) {
                it[FoodEntriesTable.status] = FoodEntryStatus.ARCHIVED.name
                it[FoodEntriesTable.updatedAt] = now
            }
            FoodEntriesTable.selectAll()
                .where {
                    (FoodEntriesTable.id eq id) and
                    (FoodEntriesTable.userId eq userId)
                }
                .singleOrNull()
                ?.toFoodEntry()
                ?: throw ResourceNotFoundException("Food entry not found: $id")
        }
    }

    override suspend fun update(
        id: Uuid,
        userId: Uuid,
        name: String,
        portionGrams: Double,
        carbsPer100g: Double,
    ): FoodEntry = withContext(ioDispatcher) {
        suspendTransaction {
            val now = Clock.System.now()
            FoodEntriesTable.update({
                (FoodEntriesTable.id eq id) and
                (FoodEntriesTable.userId eq userId)
            }) {
                it[FoodEntriesTable.name] = name
                it[FoodEntriesTable.portionGrams] = portionGrams
                it[FoodEntriesTable.carbsPer100g] = carbsPer100g
                it[FoodEntriesTable.updatedAt] = now
            }
            FoodEntriesTable.selectAll()
                .where {
                    (FoodEntriesTable.id eq id) and
                    (FoodEntriesTable.userId eq userId)
                }
                .singleOrNull()
                ?.toFoodEntry()
                ?: throw ResourceNotFoundException("Food entry not found: $id")
        }
    }

    override suspend fun delete(id: Uuid, userId: Uuid): Unit = withContext(ioDispatcher) {
        suspendTransaction {
            val deleted = FoodEntriesTable.deleteWhere {
                (FoodEntriesTable.id eq id) and
                (FoodEntriesTable.userId eq userId)
            }
            if (deleted == 0) throw ResourceNotFoundException("Food entry not found: $id")
        }
    }

    private fun ResultRow.toFoodEntry(): FoodEntry = FoodEntry(
        id = this[FoodEntriesTable.id],
        userId = this[FoodEntriesTable.userId],
        name = this[FoodEntriesTable.name],
        portionGrams = this[FoodEntriesTable.portionGrams],
        carbsPer100g = this[FoodEntriesTable.carbsPer100g],
        status = runCatching { FoodEntryStatus.valueOf(this[FoodEntriesTable.status]) }
            .getOrDefault(FoodEntryStatus.ACTIVE),
        createdAt = this[FoodEntriesTable.createdAt],
        updatedAt = this[FoodEntriesTable.updatedAt],
    )
}

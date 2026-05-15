@file:Suppress("WildcardImport", "MagicNumber", "MaxLineLength", "TooManyFunctions")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.infrastructure.persistence

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry
import org.javafreedom.kdiab.carbs.domain.model.FoodEntryStatus
import org.javafreedom.kdiab.carbs.domain.repository.FoodEntryRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.javatime.timestamp

object FoodEntriesTable : Table("food_entries") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val name = varchar("name", 200)
    val portionGrams = double("portion_grams")
    val carbsPer100g = double("carbs_per_100g")
    val status = varchar("status", 20).default("ACTIVE")
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
        suspendTransaction {
            FoodEntriesTable.insert {
                it[FoodEntriesTable.id] = entry.id
                it[FoodEntriesTable.userId] = entry.userId
                it[FoodEntriesTable.name] = entry.name
                it[FoodEntriesTable.portionGrams] = entry.portionGrams
                it[FoodEntriesTable.carbsPer100g] = entry.carbsPer100g
                it[FoodEntriesTable.status] = entry.status.name
                it[FoodEntriesTable.createdAt] = java.time.Instant.ofEpochMilli(entry.createdAt.toEpochMilliseconds())
                it[FoodEntriesTable.updatedAt] = java.time.Instant.ofEpochMilli(entry.updatedAt.toEpochMilliseconds())
            }
            entry
        }
    }

    override suspend fun archive(id: Uuid, userId: Uuid): FoodEntry = withContext(ioDispatcher) {
        suspendTransaction {
            val now = java.time.Instant.now()
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
            val now = java.time.Instant.now()
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
        createdAt = Instant.fromEpochMilliseconds(this[FoodEntriesTable.createdAt].toEpochMilli()),
        updatedAt = Instant.fromEpochMilliseconds(this[FoodEntriesTable.updatedAt].toEpochMilli()),
    )
}

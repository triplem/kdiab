@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.javafreedom.kdiab.profiles.domain.model.Insulin
import org.javafreedom.kdiab.profiles.domain.repository.InsulinRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.uuid.Uuid

object Insulins : Table("insulins") {
    private const val NAME_LENGTH = 255

    val id = uuid("id")
    val name = varchar("name", NAME_LENGTH).uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

class ExposedInsulinRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : InsulinRepository {

    override suspend fun findAll(): List<Insulin> = withContext(ioDispatcher) {
        suspendTransaction {
            Insulins.selectAll().map {
                Insulin(
                    id = it[Insulins.id],
                    name = it[Insulins.name]
                )
            }
        }
    }

    override suspend fun create(name: String): Insulin = withContext(ioDispatcher) {
        suspendTransaction {
            val newId = Uuid.random()
            Insulins.insert {
                it[Insulins.id] = newId
                it[Insulins.name] = name
            }
            Insulin(id = newId, name = name)
        }
    }

    override suspend fun update(id: Uuid, name: String): Insulin? = withContext(ioDispatcher) {
        suspendTransaction {
            val updated = Insulins.update({ Insulins.id eq id }) {
                it[Insulins.name] = name
            }
            if (updated > 0) Insulin(id = id, name = name) else null
        }
    }

    override suspend fun delete(id: Uuid): Boolean = withContext(ioDispatcher) {
        suspendTransaction {
            Insulins.deleteWhere { Insulins.id eq id } > 0
        }
    }
}

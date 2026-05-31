@file:Suppress("WildcardImport", "InjectDispatcher")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.javafreedom.kdiab.users.domain.repository.UserProfileRepository

object UserProfileTable : Table("user_profile") {
    val userId = uuid("user_id")
    val birthday = date("birthday").nullable()

    override val primaryKey = PrimaryKey(userId)
}

class ExposedUserProfileRepository : UserProfileRepository {

    override suspend fun findBirthdayByUserId(userId: Uuid): LocalDate? = withContext(Dispatchers.IO) {
        suspendTransaction {
            UserProfileTable.selectAll()
                .where { UserProfileTable.userId eq userId }
                .singleOrNull()
                ?.let { row -> row[UserProfileTable.birthday] }
        }
    }

    override suspend fun saveBirthday(userId: Uuid, birthday: LocalDate?): Unit = withContext(Dispatchers.IO) {
        suspendTransaction {
            UserProfileTable.upsert {
                it[UserProfileTable.userId] = userId
                it[UserProfileTable.birthday] = birthday
            }
        }
    }

    override suspend fun delete(userId: Uuid): Unit = withContext(Dispatchers.IO) {
        suspendTransaction {
            UserProfileTable.deleteWhere { UserProfileTable.userId eq userId }
        }
    }
}

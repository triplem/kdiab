@file:Suppress("WildcardImport")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.javafreedom.kdiab.common.domain.model.AuditLog
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.datetime.timestamp

private const val ACTION_MAX_LEN = 100
private const val IP_MAX_LEN = 100

object AuditLogsTable : Table("audit_logs") {
    val id = uuid("id")
    val doctorId = uuid("doctor_id")
    val patientId = uuid("patient_id")
    val action = varchar("action", ACTION_MAX_LEN)
    val occurredAt = timestamp("occurred_at")
    val ipAddress = varchar("ip_address", IP_MAX_LEN).nullable()
    val userAgent = text("user_agent").nullable()
    val detail = text("detail").nullable()
    override val primaryKey = PrimaryKey(id)
}

class ExposedAuditLogRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuditLogRepository {

    override suspend fun save(entry: AuditLog): Unit = withContext(ioDispatcher) {
        suspendTransaction {
            AuditLogsTable.insert {
                it[id] = entry.id
                it[doctorId] = entry.doctorId
                it[patientId] = entry.patientId
                it[action] = entry.action
                it[occurredAt] = entry.occurredAt
                it[ipAddress] = entry.ipAddress
                it[userAgent] = entry.userAgent
                it[detail] = entry.detail
            }
        }
    }

    override suspend fun findByPatientId(patientId: Uuid, from: Instant, to: Instant): List<AuditLog> =
        withContext(ioDispatcher) {
            suspendTransaction {
                AuditLogsTable.selectAll()
                    .where {
                        (AuditLogsTable.patientId eq patientId) and
                        (AuditLogsTable.occurredAt greaterEq from) and
                        (AuditLogsTable.occurredAt lessEq to)
                    }
                    .orderBy(AuditLogsTable.occurredAt, SortOrder.DESC)
                    .map { row ->
                        AuditLog(
                            id = row[AuditLogsTable.id],
                            doctorId = row[AuditLogsTable.doctorId],
                            patientId = row[AuditLogsTable.patientId],
                            action = row[AuditLogsTable.action],
                            occurredAt = row[AuditLogsTable.occurredAt],
                            ipAddress = row[AuditLogsTable.ipAddress],
                            userAgent = row[AuditLogsTable.userAgent],
                            detail = row[AuditLogsTable.detail],
                        )
                    }
            }
        }
}

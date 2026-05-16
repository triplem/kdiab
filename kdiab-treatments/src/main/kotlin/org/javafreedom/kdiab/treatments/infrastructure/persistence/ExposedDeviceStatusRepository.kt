@file:Suppress("WildcardImport", "MagicNumber", "MaxLineLength")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.infrastructure.persistence

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.javafreedom.kdiab.treatments.domain.model.DeviceStatus
import org.javafreedom.kdiab.treatments.domain.repository.DeviceStatusRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.javatime.timestamp

object DeviceStatusTable : Table("device_status") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val recordedAt = timestamp("recorded_at")
    val createdAt = timestamp("created_at")
    val device = varchar("device", 200)
    val pumpName = varchar("pump_name", 200).nullable()
    val reservoirUnits = double("reservoir_units").nullable()
    val batteryLevel = integer("battery_level").nullable()
    val pumpConnected = bool("pump_connected").nullable()

    override val primaryKey = PrimaryKey(id)
}

class ExposedDeviceStatusRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DeviceStatusRepository {

    override suspend fun save(deviceStatus: DeviceStatus): DeviceStatus = withContext(ioDispatcher) {
        suspendTransaction {
            DeviceStatusTable.insert {
                it[DeviceStatusTable.id] = deviceStatus.id
                it[DeviceStatusTable.userId] = deviceStatus.userId
                it[DeviceStatusTable.recordedAt] = java.time.Instant.ofEpochMilli(deviceStatus.recordedAt.toEpochMilliseconds())
                it[DeviceStatusTable.createdAt] = java.time.Instant.ofEpochMilli(deviceStatus.createdAt.toEpochMilliseconds())
                it[DeviceStatusTable.device] = deviceStatus.device
                it[DeviceStatusTable.pumpName] = deviceStatus.pumpName
                it[DeviceStatusTable.reservoirUnits] = deviceStatus.reservoirUnits
                it[DeviceStatusTable.batteryLevel] = deviceStatus.batteryLevel
                it[DeviceStatusTable.pumpConnected] = deviceStatus.pumpConnected
            }
            deviceStatus
        }
    }

    override suspend fun findLatestByUserId(userId: Uuid): DeviceStatus? = withContext(ioDispatcher) {
        suspendTransaction {
            DeviceStatusTable.selectAll()
                .where { DeviceStatusTable.userId eq userId }
                .orderBy(DeviceStatusTable.recordedAt, SortOrder.DESC)
                .limit(1)
                .map { it.toDeviceStatus() }
                .firstOrNull()
        }
    }

    private fun ResultRow.toDeviceStatus(): DeviceStatus = DeviceStatus(
        id = this[DeviceStatusTable.id],
        userId = this[DeviceStatusTable.userId],
        recordedAt = Instant.fromEpochMilliseconds(this[DeviceStatusTable.recordedAt].toEpochMilli()),
        createdAt = Instant.fromEpochMilliseconds(this[DeviceStatusTable.createdAt].toEpochMilli()),
        device = this[DeviceStatusTable.device],
        pumpName = this[DeviceStatusTable.pumpName],
        reservoirUnits = this[DeviceStatusTable.reservoirUnits],
        batteryLevel = this[DeviceStatusTable.batteryLevel],
        pumpConnected = this[DeviceStatusTable.pumpConnected],
    )
}

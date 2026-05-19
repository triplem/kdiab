@file:Suppress("WildcardImport", "InjectDispatcher")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.javafreedom.kdiab.users.domain.model.UserSettings
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository

object UserSettingsTable : Table("user_settings") {
    private const val UUID_LEN = 36
    private const val TIMEZONE_LEN = 64
    private const val LANG_LEN = 8
    private const val UNIT_LEN = 16
    private const val ISO_INSTANT_LEN = 50
    private const val DEFAULT_TIME_FORMAT = 24
    private const val DEFAULT_SENSOR_DURATION_HOURS = 240

    val userId = varchar("user_id", UUID_LEN)
    val timezone = varchar("timezone", TIMEZONE_LEN).default("UTC")
    val language = varchar("language", LANG_LEN).default("en")
    val timeFormat = integer("time_format").default(DEFAULT_TIME_FORMAT)
    val glucoseUnit = varchar("glucose_unit", UNIT_LEN).default("mg/dL")
    val weightUnit = varchar("weight_unit", UNIT_LEN).default("kg")
    val alarmUrgentHigh = integer("alarm_urgent_high").nullable()
    val alarmHigh = integer("alarm_high").nullable()
    val alarmLow = integer("alarm_low").nullable()
    val alarmUrgentLow = integer("alarm_urgent_low").nullable()
    val sensorDurationHours = integer("sensor_duration_hours").default(DEFAULT_SENSOR_DURATION_HOURS)
    val createdAt = varchar("created_at", ISO_INSTANT_LEN)
    val updatedAt = varchar("updated_at", ISO_INSTANT_LEN)

    override val primaryKey = PrimaryKey(userId)
}

class ExposedUserSettingsRepository : UserSettingsRepository {

    override suspend fun findByUserId(userId: Uuid): UserSettings? = withContext(Dispatchers.IO) {
        transaction {
            UserSettingsTable.selectAll()
                .where { UserSettingsTable.userId eq userId.toString() }
                .singleOrNull()
                ?.let { row ->
                    UserSettings(
                        userId = userId,
                        timezone = row[UserSettingsTable.timezone],
                        language = row[UserSettingsTable.language],
                        timeFormat = row[UserSettingsTable.timeFormat],
                        glucoseUnit = row[UserSettingsTable.glucoseUnit],
                        weightUnit = row[UserSettingsTable.weightUnit],
                        alarmUrgentHigh = row[UserSettingsTable.alarmUrgentHigh],
                        alarmHigh = row[UserSettingsTable.alarmHigh],
                        alarmLow = row[UserSettingsTable.alarmLow],
                        alarmUrgentLow = row[UserSettingsTable.alarmUrgentLow],
                        sensorDurationHours = row[UserSettingsTable.sensorDurationHours],
                        createdAt = row[UserSettingsTable.createdAt].parseInstant(),
                        updatedAt = row[UserSettingsTable.updatedAt].parseInstant(),
                    )
                }
        }
    }

    override suspend fun save(settings: UserSettings): UserSettings = withContext(Dispatchers.IO) {
        transaction {
            UserSettingsTable.upsert {
                it[userId] = settings.userId.toString()
                it[timezone] = settings.timezone
                it[language] = settings.language
                it[timeFormat] = settings.timeFormat
                it[glucoseUnit] = settings.glucoseUnit
                it[weightUnit] = settings.weightUnit
                it[alarmUrgentHigh] = settings.alarmUrgentHigh
                it[alarmHigh] = settings.alarmHigh
                it[alarmLow] = settings.alarmLow
                it[alarmUrgentLow] = settings.alarmUrgentLow
                it[sensorDurationHours] = settings.sensorDurationHours
                it[createdAt] = settings.createdAt.toString()
                it[updatedAt] = settings.updatedAt.toString()
            }
        }
        settings
    }

    override suspend fun delete(userId: Uuid): Unit = withContext(Dispatchers.IO) {
        transaction {
            UserSettingsTable.deleteWhere { UserSettingsTable.userId eq userId.toString() }
        }
    }
}

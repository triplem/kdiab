@file:Suppress("WildcardImport", "InjectDispatcher")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.javafreedom.kdiab.users.domain.model.AlarmThresholds
import org.javafreedom.kdiab.users.domain.model.DiabetesProfile
import org.javafreedom.kdiab.users.domain.model.LocalePreferences
import org.javafreedom.kdiab.users.domain.model.UnitPreferences
import org.javafreedom.kdiab.users.domain.model.UserSettings
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository

object UserSettingsTable : Table("user_settings") {
    private const val TIMEZONE_LEN = 64
    private const val LANG_LEN = 8
    private const val UNIT_LEN = 16
    private const val ISO_INSTANT_LEN = 50
    private const val DEFAULT_TIME_FORMAT = 24
    private const val DEFAULT_SENSOR_DURATION_HOURS = 240

    val userId = uuid("user_id")
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
    val birthday = date("birthday").nullable()
    val diabetesSince = integer("diabetes_since").nullable()
    val createdAt = varchar("created_at", ISO_INSTANT_LEN)
    val updatedAt = varchar("updated_at", ISO_INSTANT_LEN)

    override val primaryKey = PrimaryKey(userId)
}

class ExposedUserSettingsRepository : UserSettingsRepository {

    override suspend fun findByUserId(userId: Uuid): UserSettings? = withContext(Dispatchers.IO) {
        suspendTransaction {
            UserSettingsTable.selectAll()
                .where { UserSettingsTable.userId eq userId }
                .singleOrNull()
                ?.let { row ->
                    val alarms = AlarmThresholds.fromNullable(
                        urgentHigh = row[UserSettingsTable.alarmUrgentHigh],
                        high = row[UserSettingsTable.alarmHigh],
                        low = row[UserSettingsTable.alarmLow],
                        urgentLow = row[UserSettingsTable.alarmUrgentLow],
                    )
                    UserSettings(
                        userId = userId,
                        birthday = row[UserSettingsTable.birthday],
                        locale = LocalePreferences(
                            timezone = row[UserSettingsTable.timezone],
                            language = row[UserSettingsTable.language],
                            timeFormat = row[UserSettingsTable.timeFormat],
                        ),
                        units = UnitPreferences(
                            glucoseUnit = row[UserSettingsTable.glucoseUnit],
                            weightUnit = row[UserSettingsTable.weightUnit],
                        ),
                        alarms = alarms,
                        diabetes = DiabetesProfile(
                            sensorDurationHours = row[UserSettingsTable.sensorDurationHours],
                            diabetesSince = row[UserSettingsTable.diabetesSince],
                        ),
                        createdAt = row[UserSettingsTable.createdAt].parseInstant(),
                        updatedAt = row[UserSettingsTable.updatedAt].parseInstant(),
                    )
                }
        }
    }

    override suspend fun save(settings: UserSettings): UserSettings = withContext(Dispatchers.IO) {
        suspendTransaction {
            UserSettingsTable.upsert {
                it[userId] = settings.userId
                it[birthday] = settings.birthday
                it[timezone] = settings.locale.timezone
                it[language] = settings.locale.language
                it[timeFormat] = settings.locale.timeFormat
                it[glucoseUnit] = settings.units.glucoseUnit
                it[weightUnit] = settings.units.weightUnit
                it[alarmUrgentHigh] = settings.alarms?.urgentHigh
                it[alarmHigh] = settings.alarms?.high
                it[alarmLow] = settings.alarms?.low
                it[alarmUrgentLow] = settings.alarms?.urgentLow
                it[sensorDurationHours] = settings.diabetes.sensorDurationHours
                it[diabetesSince] = settings.diabetes.diabetesSince
                it[createdAt] = settings.createdAt.toString()
                it[updatedAt] = settings.updatedAt.toString()
            }
        }
        settings
    }

    override suspend fun delete(userId: Uuid): Unit = withContext(Dispatchers.IO) {
        suspendTransaction {
            UserSettingsTable.deleteWhere { UserSettingsTable.userId eq userId }
        }
    }
}

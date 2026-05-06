package org.javafreedom.kdiab.treatments.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*

object DatabaseFactory {
    private const val CONNECTION_TIMEOUT_MS = 30_000L
    private const val IDLE_TIMEOUT_MS = 600_000L
    private const val MAX_LIFETIME_MS = 1_800_000L
    private const val LEAK_DETECTION_THRESHOLD_MS = 60_000L

    fun init(config: ApplicationConfig) {
        val storageConfig = config.config("storage")
        val driverClassName = storageConfig.property("driverClassName").getString()
        val jdbcUrl = storageConfig.property("jdbcUrl").getString()
        val username = storageConfig.property("username").getString()
        val password = storageConfig.property("password").getString()
        val maximumPoolSize = storageConfig.property("maximumPoolSize").getString().toInt()
        val isAutoCommit = storageConfig.property("isAutoCommit").getString().toBoolean()
        val transactionIsolation = storageConfig.property("transactionIsolation").getString()

        val hikariConfig = HikariConfig().apply {
            this.driverClassName = driverClassName
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            this.maximumPoolSize = maximumPoolSize
            this.isAutoCommit = isAutoCommit
            this.transactionIsolation = transactionIsolation
            this.connectionTimeout = CONNECTION_TIMEOUT_MS
            this.idleTimeout = IDLE_TIMEOUT_MS
            this.maxLifetime = MAX_LIFETIME_MS
            this.leakDetectionThreshold = LEAK_DETECTION_THRESHOLD_MS
            validate()
        }
        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(TreatmentsTable)
        }
    }
}

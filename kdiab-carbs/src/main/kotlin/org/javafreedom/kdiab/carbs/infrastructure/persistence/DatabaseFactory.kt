package org.javafreedom.kdiab.carbs.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.ApplicationConfig
import org.javafreedom.kdiab.carbs.infrastructure.persistence.FoodEntriesTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = KotlinLogging.logger {}

object DatabaseFactory {
    private const val CONNECTION_TIMEOUT_MS = 30_000L
    private const val IDLE_TIMEOUT_MS = 600_000L
    private const val MAX_LIFETIME_MS = 1_800_000L
    private const val LEAK_DETECTION_THRESHOLD_MS = 60_000L

    // createSchema is true only in E2E tests that embed the app with H2.
    // In production the liquibase-carbs container creates the schema (DML-only app role).
    fun init(config: ApplicationConfig, createSchema: Boolean = false) {
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

        // Schema is managed exclusively by the liquibase-carbs container (runs as
        // the PostgreSQL superuser before this service starts). The app role is
        // DML-only and must not issue any DDL.
        logger.info { "Connecting to database: $jdbcUrl" }
        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        if (createSchema) {
            transaction { SchemaUtils.create(FoodEntriesTable) }
        }
    }
}

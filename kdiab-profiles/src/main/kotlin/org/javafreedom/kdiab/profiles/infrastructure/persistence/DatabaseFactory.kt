package org.javafreedom.kdiab.profiles.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.ApplicationConfig
import liquibase.Liquibase
import liquibase.database.DatabaseFactory as LiquibaseDatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Initialises the database connection pool and runs Liquibase schema migrations.
 *
 * Liquibase is used as the **single schema authority** for all environments — production
 * (PostgreSQL), E2E tests, and integration tests — so that:
 * - Every test runs against exactly the schema that production uses.
 * - Database-specific constructs (e.g. the PostgreSQL partial index and the H2 generated
 *   column for one-ACTIVE-per-user enforcement) are exercised consistently.
 * - `SchemaUtils.create` is *not* used here because it only reflects the current Exposed
 *   table definitions and would bypass Liquibase migration logic entirely.
 *
 * See ADR-113 for the full rationale.
 */
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

        val hikariConfig =
            HikariConfig().apply {
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

        // Run Liquibase migrations *before* handing connections to the application.
        // A direct DriverManager connection is used so that Liquibase manages its own
        // transaction boundary independently of the HikariCP pool.
        logger.info { "Running Liquibase migrations on $jdbcUrl" }
        java.sql.DriverManager.getConnection(jdbcUrl, username, password).use { conn ->
            val lbDatabase =
                LiquibaseDatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(JdbcConnection(conn))
            Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), lbDatabase)
                .update("")
        }
        logger.info { "Liquibase migrations completed" }

        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)
    }
}

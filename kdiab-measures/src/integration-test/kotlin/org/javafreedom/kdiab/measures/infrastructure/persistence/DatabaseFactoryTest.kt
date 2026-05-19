package org.javafreedom.kdiab.measures.infrastructure.persistence

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Verifies that [DatabaseFactory.init] establishes a working HikariCP connection pool.
 *
 * Schema creation is intentionally **not** tested here — that is the responsibility of the
 * dedicated Liquibase Docker container (production) and [LiquibaseTestHelper] (integration
 * tests). [DatabaseFactory.init] only configures the connection pool.
 */
class DatabaseFactoryTest {

    @Test
    fun `init connects to H2 and returns a usable database`() {
        val dbName = "db_factory_conn_test"
        // Bootstrap schema via LiquibaseTestHelper so the DB is ready before init().
        LiquibaseTestHelper.setup(dbName)

        val config = MapApplicationConfig(
            "storage.driverClassName" to "org.h2.Driver",
            "storage.jdbcUrl" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "storage.username" to "root",
            "storage.password" to "",
            "storage.maximumPoolSize" to "3",
            "storage.isAutoCommit" to "false",
            "storage.transactionIsolation" to "TRANSACTION_REPEATABLE_READ",
            // LiquibaseTestHelper already ran migrations above — skip to avoid "table already exists".
            "db.runMigrations" to "false",
        )

        DatabaseFactory.init(config)

        // A simple query proves the connection pool is live.
        transaction {
            val result = exec("SELECT 1") { rs -> rs.next(); rs.getInt(1) }
            assertNotNull(result, "Expected a result from SELECT 1")
        }
    }
}

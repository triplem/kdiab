package org.javafreedom.kdiab.measures.infrastructure.persistence

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class DatabaseFactoryTest {

    @Test
    fun `init runs Liquibase migrations creating both MeasuresTable and AuditLogsTable`() {
        val config = MapApplicationConfig(
            "storage.driverClassName" to "org.h2.Driver",
            "storage.jdbcUrl" to "jdbc:h2:mem:db_factory_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "storage.username" to "root",
            "storage.password" to "",
            "storage.maximumPoolSize" to "3",
            "storage.isAutoCommit" to "false",
            "storage.transactionIsolation" to "TRANSACTION_REPEATABLE_READ",
        )

        DatabaseFactory.init(config)

        // Both queries succeed only if Liquibase migrations ran and created each table.
        // A missing table causes H2 to throw JdbcSQLSyntaxErrorException, failing the test.
        transaction {
            val measureCount = exec("SELECT COUNT(*) FROM measures") { rs ->
                rs.next(); rs.getLong(1)
            }
            val auditCount = exec("SELECT COUNT(*) FROM audit_logs") { rs ->
                rs.next(); rs.getLong(1)
            }
            assertEquals(0L, measureCount, "measures table should exist and be empty")
            assertEquals(0L, auditCount, "audit_logs table should exist and be empty")
        }
    }
}

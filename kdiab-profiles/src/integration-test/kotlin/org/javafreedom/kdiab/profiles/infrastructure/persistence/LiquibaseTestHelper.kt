package org.javafreedom.kdiab.profiles.infrastructure.persistence

import liquibase.Liquibase
import liquibase.database.DatabaseFactory as LiquibaseDatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.DriverManager

/**
 * Bootstraps an H2 in-memory database for integration tests by running the full Liquibase
 * changelog — the same migration path that PostgreSQL takes in production.
 *
 * Each integration test class passes a **unique [dbName]** so that concurrent test suites
 * do not share state. The Exposed [Database] instance is stored and returned so callers can
 * bind transactions explicitly and avoid cross-suite pollution via the thread-local default.
 *
 * Cleanup between tests is done with targeted `DELETE` statements (see [cleanData]) rather
 * than drop/recreate, because Liquibase tracks already-executed changesets in the
 * `DATABASECHANGELOG` table — dropping the schema would force a re-run that is both slow
 * and unnecessary.
 */
object LiquibaseTestHelper {

    private const val DRIVER = "org.h2.Driver"
    private const val USER = "root"
    private const val PASSWORD = ""

    /**
     * Connects [dbName] to an in-memory H2 database in PostgreSQL compatibility mode,
     * runs all Liquibase migrations, and returns the Exposed [Database] instance.
     *
     * Safe to call multiple times for the same [dbName] — Liquibase skips already-applied
     * changesets via the `DATABASECHANGELOG` tracking table.
     */
    fun setup(dbName: String): Database {
        val jdbcUrl = "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
        Class.forName(DRIVER)

        val db = Database.connect(url = jdbcUrl, driver = DRIVER, user = USER, password = PASSWORD)

        DriverManager.getConnection(jdbcUrl, USER, PASSWORD).use { conn ->
            val lbDatabase =
                LiquibaseDatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(JdbcConnection(conn))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                lbDatabase
            ).update("")
        }

        return db
    }

    /**
     * Deletes all domain data from [db] while preserving the schema and the Liquibase
     * tracking tables. Call this in `@BeforeTest` so each test starts with a clean slate.
     *
     * Deletion order respects FK constraints:
     * 1. `profile_statuses` (child of `profiles`)
     * 2. `profiles` (self-referential FK is safe to bulk-delete)
     * 3. `insulins`
     *
     * Seed data inserted by Liquibase (insulin reference data) is also removed so that
     * repository tests that count rows are not surprised by pre-existing rows.
     */
    fun cleanData(db: Database) {
        transaction(db) {
            exec("DELETE FROM profile_statuses")
            exec("DELETE FROM profiles")
            exec("DELETE FROM insulins")
        }
    }
}

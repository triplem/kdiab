package org.javafreedom.kdiab.users.infrastructure.persistence

import liquibase.Liquibase
import liquibase.database.DatabaseFactory as LiquibaseDatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.DriverManager

object LiquibaseTestHelper {

    private const val DRIVER = "org.h2.Driver"
    private const val USER = "root"
    private const val PASSWORD = ""

    fun setup(dbName: String): Database {
        val jdbcUrl = "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
        Class.forName(DRIVER)
        val db = Database.connect(url = jdbcUrl, driver = DRIVER, user = USER, password = PASSWORD)
        DriverManager.getConnection(jdbcUrl, USER, PASSWORD).use { conn ->
            val lbDb = LiquibaseDatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(conn))
            Liquibase("db/changelog/db.changelog-root.yaml", ClassLoaderResourceAccessor(), lbDb)
                .update("")
        }
        return db
    }

    fun cleanData(db: Database) {
        transaction(db) {
            exec("DELETE FROM doctor_patient")
            exec("DELETE FROM user_settings")
        }
    }
}

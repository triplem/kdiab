package org.javafreedom.kdiab.treatments.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*

object DatabaseFactory {
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
            validate()
        }
        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(TreatmentsTable)
        }
    }
}

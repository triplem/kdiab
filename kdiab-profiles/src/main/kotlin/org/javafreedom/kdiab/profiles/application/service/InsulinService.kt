@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.application.service

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.profiles.domain.model.Insulin as DomainInsulin
import org.javafreedom.kdiab.profiles.domain.repository.InsulinRepository

class InsulinService(private val repository: InsulinRepository) {

    suspend fun findAll(): List<DomainInsulin> = repository.findAll()

    suspend fun create(name: String): DomainInsulin {
        return try {
            repository.create(name)
        } catch (e: org.jetbrains.exposed.v1.exceptions.ExposedSQLException) {
            throw ConflictException("An insulin with that name already exists", e)
        }
    }

    suspend fun update(id: Uuid, name: String): DomainInsulin? {
        return try {
            repository.update(id, name)
        } catch (e: org.jetbrains.exposed.v1.exceptions.ExposedSQLException) {
            throw ConflictException("An insulin with that name already exists", e)
        }
    }

    suspend fun delete(id: Uuid): Boolean = repository.delete(id)
}

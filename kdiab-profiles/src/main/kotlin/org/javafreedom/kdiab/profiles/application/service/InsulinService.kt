@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.application.service

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.domain.model.Insulin as DomainInsulin
import org.javafreedom.kdiab.profiles.domain.repository.InsulinRepository

class InsulinService(private val repository: InsulinRepository) {

    suspend fun findAll(): List<DomainInsulin> = repository.findAll()

    suspend fun create(name: String): DomainInsulin = repository.create(name)

    suspend fun update(id: Uuid, name: String): DomainInsulin? = repository.update(id, name)

    suspend fun delete(id: Uuid): Boolean = repository.delete(id)
}

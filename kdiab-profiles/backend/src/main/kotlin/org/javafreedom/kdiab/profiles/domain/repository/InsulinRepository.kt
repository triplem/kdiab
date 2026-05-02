package org.javafreedom.kdiab.profiles.domain.repository

import org.javafreedom.kdiab.profiles.domain.model.Insulin

interface InsulinRepository {
    suspend fun findAll(): List<Insulin>
    suspend fun create(name: String): Insulin
    suspend fun update(id: kotlin.uuid.Uuid, name: String): Insulin?
    suspend fun delete(id: kotlin.uuid.Uuid): Boolean
}

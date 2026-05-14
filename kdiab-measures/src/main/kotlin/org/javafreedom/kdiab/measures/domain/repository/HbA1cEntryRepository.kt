@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.domain.repository

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.measures.domain.model.HbA1cEntry

interface HbA1cEntryRepository {
    suspend fun save(entry: HbA1cEntry): HbA1cEntry
    suspend fun findByUserIdBetween(userId: Uuid, from: Instant?, to: Instant?): List<HbA1cEntry>
}

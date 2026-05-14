@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.application.service

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.measures.domain.model.HbA1cEntry
import org.javafreedom.kdiab.measures.domain.repository.HbA1cEntryRepository

class HbA1cEntryService(
    private val hbA1cEntryRepository: HbA1cEntryRepository,
) {

    suspend fun createEntry(entry: HbA1cEntry, principal: UserPrincipal?, targetUserId: Uuid): HbA1cEntry {
        if (principal == null || !principal.canAccess(targetUserId)) {
            throw AuthorizationException("Access Not Authorized")
        }
        return hbA1cEntryRepository.save(entry)
    }

    suspend fun listEntries(
        targetUserId: Uuid,
        from: Instant?,
        to: Instant?,
        principal: UserPrincipal?,
    ): List<HbA1cEntry> {
        if (principal == null || !principal.canAccess(targetUserId)) {
            throw AuthorizationException("Access Not Authorized")
        }
        return hbA1cEntryRepository.findByUserIdBetween(targetUserId, from, to)
    }
}

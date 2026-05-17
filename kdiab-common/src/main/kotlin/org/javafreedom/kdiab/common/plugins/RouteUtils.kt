@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.common.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException

fun parseUuid(rawId: String, paramName: String = "userId"): Uuid =
    runCatching { Uuid.parse(rawId) }.getOrElse {
        throw BusinessValidationException("Invalid $paramName format: $rawId")
    }

fun ApplicationCall.requireUserPrincipal(): UserPrincipal =
    principal<UserPrincipal>() ?: throw AuthorizationException("Access Not Authorized")

fun checkReadAccess(principal: UserPrincipal?, targetUserId: Uuid) {
    if (principal == null || !principal.canAccess(targetUserId)) {
        throw AuthorizationException("Access Not Authorized")
    }
}

fun checkWriteAccess(principal: UserPrincipal?, targetUserId: Uuid) {
    if (principal == null || !principal.canAccess(targetUserId)) {
        throw AuthorizationException("Access Not Authorized")
    }
}

data class PaginationParams(val page: Int, val size: Int) {
    val offset: Long get() = page.toLong() * size
    companion object {
        fun from(page: Int?, size: Int?) = PaginationParams(
            page = (page ?: 0).coerceAtLeast(0),
            size = (size ?: 20).coerceIn(1, 100),
        )
    }
}

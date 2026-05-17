@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.common.plugins.checkReadAccess as commonCheckReadAccess
import org.javafreedom.kdiab.common.plugins.parseUuid as commonParseUuid

internal fun parseUuid(value: String): Uuid = commonParseUuid(value)

internal fun checkAccess(principal: UserPrincipal?, targetUserId: Uuid) =
    commonCheckReadAccess(principal, targetUserId)

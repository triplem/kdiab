package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlin.time.Instant

// PostgreSQL returns timestamps with a space separator ('2026-02-18 17:46:47+00') when stored
// in varchar columns. Kotlin's Instant.parse() requires ISO 8601 with 'T' and a full offset.
internal fun String.parseInstant(): Instant =
    Instant.parse(replace(' ', 'T').replace(Regex("""([+-]\d{2})$"""), "$1:00"))

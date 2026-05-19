package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ParseInstantTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        // PostgreSQL varchar format (space separator, bare +00 offset)
        "2026-02-18 17:46:47.679810+00, 2026-02-18T17:46:47.679810Z",
        // PostgreSQL with full offset
        "2026-02-18 17:46:47.679810+00:00, 2026-02-18T17:46:47.679810Z",
        // ISO 8601 written by Instant.toString() — must round-trip unchanged
        "2026-02-18T17:46:47.679810Z, 2026-02-18T17:46:47.679810Z",
        // Negative offset
        "2026-02-18 17:46:47.000000-05, 2026-02-18T17:46:47-05:00",
    )
    fun `parseInstant handles all timestamp formats`(input: String, expectedIso: String) {
        val result = input.parseInstant()
        assertEquals(Instant.parse(expectedIso), result)
    }

    @Test
    fun `parseInstant on ISO 8601 Z string is a no-op`() {
        val iso = "2026-02-18T17:46:47.679810Z"
        assertEquals(Instant.parse(iso), iso.parseInstant())
    }
}

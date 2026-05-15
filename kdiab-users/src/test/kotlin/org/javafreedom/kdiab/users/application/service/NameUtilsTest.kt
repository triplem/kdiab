package org.javafreedom.kdiab.users.application.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NameUtilsTest {

    @Test
    fun `splitDisplayName single token returns full name as firstName and null lastName`() {
        val (first, last) = splitDisplayName("García")
        assertEquals("García", first)
        assertNull(last)
    }

    @Test
    fun `splitDisplayName two tokens splits on space`() {
        val (first, last) = splitDisplayName("John Doe")
        assertEquals("John", first)
        assertEquals("Doe", last)
    }

    @Test
    fun `splitDisplayName multi-token uses last space as boundary`() {
        val (first, last) = splitDisplayName("José María García")
        assertEquals("José María", first)
        assertEquals("García", last)
    }

    @Test
    fun `splitDisplayName preserves non-ASCII characters`() {
        val (first, last) = splitDisplayName("李 明")
        assertEquals("李", first)
        assertEquals("明", last)
    }
}

package org.javafreedom.kdiab.treatments.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertContains

class RoleTest {

    @Test
    fun `fromString returns PATIENT for case-insensitive input`() {
        assertEquals(Role.PATIENT, Role.fromString("PATIENT"))
        assertEquals(Role.PATIENT, Role.fromString("patient"))
        assertEquals(Role.PATIENT, Role.fromString("Patient"))
    }

    @Test
    fun `fromString returns DOCTOR for case-insensitive input`() {
        assertEquals(Role.DOCTOR, Role.fromString("DOCTOR"))
        assertEquals(Role.DOCTOR, Role.fromString("doctor"))
        assertEquals(Role.DOCTOR, Role.fromString("Doctor"))
    }

    @Test
    fun `fromString returns ADMIN for case-insensitive input`() {
        assertEquals(Role.ADMIN, Role.fromString("ADMIN"))
        assertEquals(Role.ADMIN, Role.fromString("admin"))
        assertEquals(Role.ADMIN, Role.fromString("Admin"))
    }

    @Test
    fun `fromString returns null for unknown role`() {
        assertNull(Role.fromString("USER"))
        assertNull(Role.fromString("SUPERUSER"))
        assertNull(Role.fromString(""))
        assertNull(Role.fromString("NURSE"))
    }

    @Test
    fun `exactly three roles are defined`() {
        val names = Role.entries.map { it.name }
        assertContains(names, "PATIENT")
        assertContains(names, "DOCTOR")
        assertContains(names, "ADMIN")
        assertEquals(3, Role.entries.size)
    }
}

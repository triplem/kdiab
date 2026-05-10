@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.plugins

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal

class UserPrincipalTest {

    private val sarahId  = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val mikeId   = Uuid.parse("22222222-2222-2222-2222-222222222222")
    private val doctorId = Uuid.parse("33333333-3333-3333-3333-333333333333")
    private val adminId  = Uuid.parse("55555555-5555-5555-5555-555555555555")

    @Test
    fun `isAdmin true only for ADMIN role`() {
        assertTrue(UserPrincipal(adminId, setOf(Role.ADMIN), emptySet()).isAdmin())
        assertFalse(UserPrincipal(sarahId, setOf(Role.PATIENT), emptySet()).isAdmin())
        assertFalse(UserPrincipal(doctorId, setOf(Role.DOCTOR), emptySet()).isAdmin())
    }

    @Test
    fun `isDoctor true only for DOCTOR role`() {
        assertTrue(UserPrincipal(doctorId, setOf(Role.DOCTOR), emptySet()).isDoctor())
        assertFalse(UserPrincipal(sarahId, setOf(Role.PATIENT), emptySet()).isDoctor())
        assertFalse(UserPrincipal(adminId, setOf(Role.ADMIN), emptySet()).isDoctor())
    }

    @Test
    fun `canAccess - patient can access own resources`() {
        val sarah = UserPrincipal(sarahId, setOf(Role.PATIENT), emptySet())
        assertTrue(sarah.canAccess(sarahId))
    }

    @Test
    fun `canAccess - patient cannot access another patient resources`() {
        val sarah = UserPrincipal(sarahId, setOf(Role.PATIENT), emptySet())
        assertFalse(sarah.canAccess(mikeId))
    }

    @Test
    fun `canAccess - admin can access any user resources`() {
        val admin = UserPrincipal(adminId, setOf(Role.ADMIN), emptySet())
        assertTrue(admin.canAccess(sarahId))
        assertTrue(admin.canAccess(mikeId))
        assertTrue(admin.canAccess(adminId))
    }

    @Test
    fun `canAccess - doctor can access own resources`() {
        val doctor = UserPrincipal(doctorId, setOf(Role.DOCTOR), emptySet())
        assertTrue(doctor.canAccess(doctorId))
    }

    @Test
    fun `canAccess - doctor can access allowed patient resources`() {
        val doctor = UserPrincipal(doctorId, setOf(Role.DOCTOR), setOf(sarahId))
        assertTrue(doctor.canAccess(sarahId))
    }

    @Test
    fun `canAccess - doctor cannot access non-allowed patient resources`() {
        val doctor = UserPrincipal(doctorId, setOf(Role.DOCTOR), setOf(sarahId))
        assertFalse(doctor.canAccess(mikeId))
    }

    @Test
    fun `canAccess - doctor with empty allowed list cannot access patients`() {
        val doctor = UserPrincipal(doctorId, setOf(Role.DOCTOR), emptySet())
        assertFalse(doctor.canAccess(sarahId))
    }
}

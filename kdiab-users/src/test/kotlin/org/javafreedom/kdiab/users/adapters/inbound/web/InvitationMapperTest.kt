@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.users.application.service.InvitationListResult
import org.javafreedom.kdiab.users.domain.model.DoctorInvitation
import org.javafreedom.kdiab.users.domain.model.InvitationAction
import org.javafreedom.kdiab.users.domain.model.InvitationStatus

class InvitationMapperTest {

    private val invitationId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val doctorId = Uuid.parse("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val patientId = Uuid.parse("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val now = Clock.System.now()
    private val later = now.plus(kotlin.time.Duration.parse("72h"))

    private fun invitation(
        status: InvitationStatus = InvitationStatus.PENDING,
        resolvedAt: kotlin.time.Instant? = null,
        resolvedPatientId: Uuid? = null,
    ) = DoctorInvitation(
        id = invitationId,
        doctorId = doctorId,
        patientIdentifier = "patient@example.com",
        patientId = resolvedPatientId,
        status = status,
        message = "Please join my practice",
        createdAt = now,
        expiresAt = later,
        resolvedAt = resolvedAt,
    )

    // ── DoctorInvitation.toResponse() ─────────────────────────────────────────

    @Test
    fun `toResponse maps all mandatory fields`() {
        val response = invitation().toResponse()
        assertEquals(invitationId.toString(), response.id)
        assertEquals(doctorId.toString(), response.doctorId)
        assertEquals("patient@example.com", response.patientIdentifier)
        assertEquals("PENDING", response.status)
        assertEquals(now.toString(), response.createdAt)
        assertEquals(later.toString(), response.expiresAt)
    }

    @Test
    fun `toResponse has null patientId when invitation not yet resolved`() {
        val response = invitation(resolvedPatientId = null).toResponse()
        assertNull(response.patientId)
    }

    @Test
    fun `toResponse maps patientId when invitation is resolved`() {
        val response = invitation(resolvedPatientId = patientId).toResponse()
        assertEquals(patientId.toString(), response.patientId)
    }

    @Test
    fun `toResponse has null resolvedAt when invitation is still pending`() {
        val response = invitation(resolvedAt = null).toResponse()
        assertNull(response.resolvedAt)
    }

    @Test
    fun `toResponse maps resolvedAt when invitation is resolved`() {
        val response = invitation(status = InvitationStatus.ACCEPTED, resolvedAt = now).toResponse()
        assertEquals(now.toString(), response.resolvedAt)
    }

    @Test
    fun `toResponse maps status name correctly for each status value`() {
        for (status in InvitationStatus.entries) {
            val response = invitation(status = status).toResponse()
            assertEquals(status.name, response.status)
        }
    }

    @Test
    fun `toResponse omits display names when not provided`() {
        val response = invitation().toResponse()
        assertNull(response.doctorDisplayName)
        assertNull(response.patientDisplayName)
    }

    @Test
    fun `toResponse includes doctorDisplayName when provided`() {
        val response = invitation().toResponse(doctorDisplayName = "Dr. House")
        assertEquals("Dr. House", response.doctorDisplayName)
    }

    @Test
    fun `toResponse includes patientDisplayName when provided`() {
        val response = invitation().toResponse(patientDisplayName = "Sarah Patient")
        assertEquals("Sarah Patient", response.patientDisplayName)
    }

    @Test
    fun `toResponse includes both display names when provided`() {
        val response = invitation().toResponse(
            doctorDisplayName = "Dr. Cameron",
            patientDisplayName = "Mike Patient",
        )
        assertEquals("Dr. Cameron", response.doctorDisplayName)
        assertEquals("Mike Patient", response.patientDisplayName)
    }

    // ── RespondToInvitationRequest.toAction() ─────────────────────────────────

    @Test
    fun `toAction returns ACCEPT for accept string`() {
        val req = RespondToInvitationRequest(action = "ACCEPT")
        assertEquals(InvitationAction.ACCEPT, req.toAction())
    }

    @Test
    fun `toAction returns DECLINE for decline string`() {
        val req = RespondToInvitationRequest(action = "DECLINE")
        assertEquals(InvitationAction.DECLINE, req.toAction())
    }

    @Test
    fun `toAction is case-insensitive`() {
        assertEquals(InvitationAction.ACCEPT, RespondToInvitationRequest("accept").toAction())
        assertEquals(InvitationAction.ACCEPT, RespondToInvitationRequest("Accept").toAction())
        assertEquals(InvitationAction.DECLINE, RespondToInvitationRequest("decline").toAction())
        assertEquals(InvitationAction.DECLINE, RespondToInvitationRequest("Decline").toAction())
    }

    @Test
    fun `toAction throws IllegalArgumentException for unknown action`() {
        val req = RespondToInvitationRequest(action = "APPROVE")
        assertFailsWith<IllegalArgumentException> { req.toAction() }
    }

    @Test
    fun `toAction throws IllegalArgumentException for empty string`() {
        val req = RespondToInvitationRequest(action = "")
        assertFailsWith<IllegalArgumentException> { req.toAction() }
    }

    // ── InvitationListResult.toPageResponse() ─────────────────────────────────

    @Test
    fun `toPageResponse maps pagination metadata`() {
        val result = InvitationListResult(
            invitations = emptyList(),
            doctorDisplayName = null,
            patientDisplayNames = emptyMap(),
            page = 2,
            size = 10,
            totalElements = 25L,
            totalPages = 3,
        )
        val response = result.toPageResponse()
        assertEquals(2, response.page)
        assertEquals(10, response.size)
        assertEquals(25L, response.totalElements)
        assertEquals(3, response.totalPages)
        assertEquals(emptyList(), response.content)
    }

    @Test
    fun `toPageResponse maps invitation content`() {
        val result = InvitationListResult(
            invitations = listOf(invitation()),
            doctorDisplayName = null,
            patientDisplayNames = emptyMap(),
            page = 0,
            size = 20,
            totalElements = 1L,
            totalPages = 1,
        )
        val response = result.toPageResponse()
        assertEquals(1, response.content.size)
        assertEquals(invitationId.toString(), response.content[0].id)
    }

    @Test
    fun `toPageResponse resolves doctorDisplayName for all invitations`() {
        val result = InvitationListResult(
            invitations = listOf(invitation(), invitation()),
            doctorDisplayName = "Dr. House",
            patientDisplayNames = emptyMap(),
            page = 0,
            size = 20,
            totalElements = 2L,
            totalPages = 1,
        )
        val response = result.toPageResponse()
        assertEquals(2, response.content.size)
        assertEquals("Dr. House", response.content[0].doctorDisplayName)
        assertEquals("Dr. House", response.content[1].doctorDisplayName)
    }

    @Test
    fun `toPageResponse resolves patientDisplayName from map by patientId`() {
        val resolvedInvitation = invitation(resolvedPatientId = patientId)
        val result = InvitationListResult(
            invitations = listOf(resolvedInvitation),
            doctorDisplayName = null,
            patientDisplayNames = mapOf(patientId.toString() to "Sarah Patient"),
            page = 0,
            size = 20,
            totalElements = 1L,
            totalPages = 1,
        )
        val response = result.toPageResponse()
        assertEquals("Sarah Patient", response.content[0].patientDisplayName)
    }

    @Test
    fun `toPageResponse has null patientDisplayName when patientId not in map`() {
        val resolvedInvitation = invitation(resolvedPatientId = patientId)
        val result = InvitationListResult(
            invitations = listOf(resolvedInvitation),
            doctorDisplayName = null,
            patientDisplayNames = emptyMap(),
            page = 0,
            size = 20,
            totalElements = 1L,
            totalPages = 1,
        )
        val response = result.toPageResponse()
        assertNull(response.content[0].patientDisplayName)
    }

    @Test
    fun `toPageResponse has null patientDisplayName when patientId is null`() {
        val pendingInvitation = invitation(resolvedPatientId = null)
        val result = InvitationListResult(
            invitations = listOf(pendingInvitation),
            doctorDisplayName = null,
            patientDisplayNames = mapOf("some-other-id" to "Other Patient"),
            page = 0,
            size = 20,
            totalElements = 1L,
            totalPages = 1,
        )
        val response = result.toPageResponse()
        assertNull(response.content[0].patientDisplayName)
    }
}

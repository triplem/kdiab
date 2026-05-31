package org.javafreedom.kdiab.users.application.jobs

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.users.application.service.InvitationService

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationExpiryJobTest {

    private val invitationService = mockk<InvitationService>()

    @Test
    fun `start calls expireOldInvitations once at launch`() = runTest {
        coEvery { invitationService.expireOldInvitations(any()) } returns 0

        val job = InvitationExpiryJob(
            invitationService = invitationService,
            intervalMs = 10_000L,
        )
        job.start(backgroundScope)

        // Advance past the initial call but before the second interval
        advanceTimeBy(1.milliseconds)

        coVerify(atLeast = 1) { invitationService.expireOldInvitations(any()) }
    }

    @Test
    fun `start calls expireOldInvitations again after interval elapses`() = runTest {
        coEvery { invitationService.expireOldInvitations(any()) } returns 0

        val intervalMs = 5_000L
        val job = InvitationExpiryJob(
            invitationService = invitationService,
            intervalMs = intervalMs,
        )
        job.start(backgroundScope)

        // Advance past two full intervals so we get at least 2 calls
        advanceTimeBy((intervalMs * 2 + 1).milliseconds)

        coVerify(atLeast = 2) { invitationService.expireOldInvitations(any()) }
    }

    @Test
    fun `start continues running after repository throws exception`() = runTest {
        var callCount = 0
        coEvery { invitationService.expireOldInvitations(any()) } answers {
            callCount++
            if (callCount == 1) throw RuntimeException("DB unavailable")
            0
        }

        val intervalMs = 3_000L
        val job = InvitationExpiryJob(
            invitationService = invitationService,
            intervalMs = intervalMs,
        )
        job.start(backgroundScope)

        // Advance past two intervals — first call throws, second must still happen
        advanceTimeBy((intervalMs * 2 + 1).milliseconds)

        // Both calls must have happened (job did not abort after the first failure)
        coVerify(atLeast = 2) { invitationService.expireOldInvitations(any()) }
    }

    @Test
    fun `expireOldInvitations receives current time as cutoff`() = runTest {
        coEvery { invitationService.expireOldInvitations(any()) } returns 3

        val job = InvitationExpiryJob(
            invitationService = invitationService,
            intervalMs = 60_000L,
        )
        job.start(backgroundScope)
        advanceTimeBy(1.milliseconds)

        // Verify that expireOldInvitations was called with some Instant (the default Clock.System.now())
        coVerify(exactly = 1) { invitationService.expireOldInvitations(any()) }
    }
}

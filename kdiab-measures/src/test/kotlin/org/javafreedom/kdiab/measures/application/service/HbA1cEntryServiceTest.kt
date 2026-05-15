@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.measures.domain.model.HbA1cEntry
import org.javafreedom.kdiab.measures.domain.model.HbA1cSource
import org.javafreedom.kdiab.measures.domain.repository.HbA1cEntryRepository

class HbA1cEntryServiceTest {

    private val repo = mockk<HbA1cEntryRepository>()
    private val service = HbA1cEntryService(repo)

    private val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val entryId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val now = Instant.parse("2024-06-01T10:00:00Z")

    private fun testPrincipal(id: Uuid = userId) = UserPrincipal(id, setOf(Role.PATIENT), emptySet())

    private fun testEntry() = HbA1cEntry(
        id = entryId,
        userId = userId,
        measuredAt = now,
        valuePercent = 6.5,
        source = HbA1cSource.LAB,
        notes = null,
        createdAt = now,
    )

    @Test
    fun `createEntry saves and returns the entry`() = runTest {
        val entry = testEntry()
        coEvery { repo.save(entry) } returns entry
        val result = service.createEntry(entry, testPrincipal(), userId)
        assertEquals(entry, result)
        coVerify(exactly = 1) { repo.save(entry) }
    }

    @Test
    fun `createEntry throws AuthorizationException when principal is null`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.createEntry(testEntry(), null, userId)
        }
        coVerify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `createEntry throws AuthorizationException when principal cannot access target user`() = runTest {
        val otherUserId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val principal = testPrincipal()
        assertFailsWith<AuthorizationException> {
            service.createEntry(testEntry(), principal, otherUserId)
        }
        coVerify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `listEntries returns entries from repository`() = runTest {
        val entries = listOf(testEntry())
        coEvery { repo.findByUserIdBetween(userId, null, null) } returns entries
        val result = service.listEntries(userId, null, null, testPrincipal())
        assertEquals(entries, result)
    }

    @Test
    fun `listEntries passes from and to to repository`() = runTest {
        val from = Instant.parse("2024-01-01T00:00:00Z")
        val to = Instant.parse("2024-06-30T23:59:59Z")
        val entries = listOf(testEntry())
        coEvery { repo.findByUserIdBetween(userId, from, to) } returns entries
        val result = service.listEntries(userId, from, to, testPrincipal())
        assertEquals(entries, result)
    }

    @Test
    fun `listEntries throws AuthorizationException when principal is null`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.listEntries(userId, null, null, null)
        }
        coVerify(exactly = 0) { repo.findByUserIdBetween(any(), any(), any()) }
    }

    @Test
    fun `listEntries throws AuthorizationException when principal cannot access target user`() = runTest {
        val otherUserId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        assertFailsWith<AuthorizationException> {
            service.listEntries(otherUserId, null, null, testPrincipal())
        }
        coVerify(exactly = 0) { repo.findByUserIdBetween(any(), any(), any()) }
    }

    @Test
    fun `admin principal can access any user entries`() = runTest {
        val adminPrincipal = UserPrincipal(Uuid.parse("99999999-9999-9999-9999-999999999999"), setOf(Role.ADMIN), emptySet())
        val entries = listOf(testEntry())
        coEvery { repo.findByUserIdBetween(userId, null, null) } returns entries
        val result = service.listEntries(userId, null, null, adminPrincipal)
        assertEquals(entries, result)
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.profiles.domain.exception.ConflictException
import org.javafreedom.kdiab.profiles.domain.model.Insulin
import org.javafreedom.kdiab.profiles.domain.repository.InsulinRepository

class InsulinServiceTest {

    private val repository = mockk<InsulinRepository>()
    private val service = InsulinService(repository)

    @Test
    fun `findAll - empty repository returns empty list`() = runBlocking {
        coEvery { repository.findAll() } returns emptyList()

        val result = service.findAll()

        assertEquals(emptyList(), result)
        coVerify(exactly = 1) { repository.findAll() }
    }

    @Test
    fun `findAll - returns all insulins from repository`() = runBlocking {
        val insulins = listOf(
            Insulin(name = "Fiasp"),
            Insulin(name = "NovoRapid")
        )
        coEvery { repository.findAll() } returns insulins

        val result = service.findAll()

        assertEquals(insulins, result)
        coVerify(exactly = 1) { repository.findAll() }
    }

    @Test
    fun `create - returns saved insulin`() = runBlocking {
        val insulin = Insulin(name = "Fiasp")
        coEvery { repository.create("Fiasp") } returns insulin

        val result = service.create("Fiasp")

        assertEquals(insulin, result)
        coVerify(exactly = 1) { repository.create("Fiasp") }
    }

    @Test
    fun `create - duplicate name throws ConflictException`() = runBlocking {
        coEvery { repository.create("Fiasp") } throws
            io.mockk.mockk<org.jetbrains.exposed.v1.exceptions.ExposedSQLException>(relaxed = true)

        assertFailsWith<ConflictException> {
            service.create("Fiasp")
        }
        Unit
    }

    @Test
    fun `update - returns updated insulin`() = runBlocking {
        val id = Uuid.random()
        val insulin = Insulin(id = id, name = "NovoRapid")
        coEvery { repository.update(id, "NovoRapid") } returns insulin

        val result = service.update(id, "NovoRapid")

        assertEquals(insulin, result)
        coVerify(exactly = 1) { repository.update(id, "NovoRapid") }
    }

    @Test
    fun `update - duplicate name throws ConflictException`() = runBlocking {
        val id = Uuid.random()
        coEvery { repository.update(id, "Fiasp") } throws
            io.mockk.mockk<org.jetbrains.exposed.v1.exceptions.ExposedSQLException>(relaxed = true)

        assertFailsWith<ConflictException> {
            service.update(id, "Fiasp")
        }
        Unit
    }

    @Test
    fun `delete - delegates to repository deleteById`() = runBlocking {
        val id = Uuid.random()
        coEvery { repository.delete(id) } returns true

        val result = service.delete(id)

        assertEquals(true, result)
        coVerify(exactly = 1) { repository.delete(id) }
    }
}

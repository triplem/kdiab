package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Repository-level integration tests for [ExposedInsulinRepository].
 *
 * Schema is bootstrapped via Liquibase (see [LiquibaseTestHelper]), including the
 * initial insulin reference data inserted by changeset 002. Data is cleared before each
 * test so that row-count assertions are not affected by seeded rows.
 */
class ExposedInsulinRepositoryTest {

    private lateinit var repository: ExposedInsulinRepository

    companion object {
        private val db: Database = LiquibaseTestHelper.setup("test_insulin_repo")
    }

    @BeforeTest
    fun setup() {
        LiquibaseTestHelper.cleanData(db)
        repository = ExposedInsulinRepository()
    }

    @Test
    fun `findAll should return empty list after data cleanup`() = runBlocking {
        val results = repository.findAll()
        assertEquals(0, results.size)
    }

    @Test
    fun `create should add insulin and findAll should return it`() = runBlocking {
        val created = repository.create("Novolog")
        assertNotNull(created.id)
        assertEquals("Novolog", created.name)

        val results = repository.findAll()
        assertEquals(1, results.size)
        assertEquals("Novolog", results[0].name)
    }

    @Test
    fun `create multiple insulins should all be returned by findAll`() = runBlocking {
        repository.create("Humalog")
        repository.create("Fiasp")

        val results = repository.findAll()
        assertEquals(2, results.size)
        val names = results.map { it.name }
        assert(names.contains("Humalog")) { "Expected Humalog in $names" }
        assert(names.contains("Fiasp")) { "Expected Fiasp in $names" }
    }
}

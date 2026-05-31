@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database

class ExposedUserProfileRepositoryTest {

    companion object {
        val db: Database = LiquibaseTestHelper.setup("user_profile_repo_test")
    }

    private val repo = ExposedUserProfileRepository()

    @BeforeTest
    fun setUp() = LiquibaseTestHelper.cleanData(db)

    @Test
    fun `findBirthdayByUserId returns null when no row exists`() = runTest {
        val result = repo.findBirthdayByUserId(Uuid.random())
        assertNull(result)
    }

    @Test
    fun `saveBirthday and findBirthdayByUserId round-trip`() = runTest {
        val userId = Uuid.random()
        val birthday = LocalDate(1990, 5, 15)
        repo.saveBirthday(userId, birthday)
        val result = repo.findBirthdayByUserId(userId)
        assertEquals(birthday, result)
    }

    @Test
    fun `saveBirthday persists null birthday`() = runTest {
        val userId = Uuid.random()
        repo.saveBirthday(userId, null)
        val result = repo.findBirthdayByUserId(userId)
        assertNull(result)
    }

    @Test
    fun `saveBirthday upserts on second call`() = runTest {
        val userId = Uuid.random()
        val first = LocalDate(1990, 5, 15)
        val second = LocalDate(1985, 3, 22)
        repo.saveBirthday(userId, first)
        repo.saveBirthday(userId, second)
        val result = repo.findBirthdayByUserId(userId)
        assertEquals(second, result)
    }

    @Test
    fun `saveBirthday clears birthday to null via upsert`() = runTest {
        val userId = Uuid.random()
        repo.saveBirthday(userId, LocalDate(1990, 5, 15))
        repo.saveBirthday(userId, null)
        val result = repo.findBirthdayByUserId(userId)
        assertNull(result)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val userId = Uuid.random()
        repo.saveBirthday(userId, LocalDate(1990, 5, 15))
        repo.delete(userId)
        val result = repo.findBirthdayByUserId(userId)
        assertNull(result)
    }

    @Test
    fun `delete is a no-op when row does not exist`() = runTest {
        // Should not throw
        repo.delete(Uuid.random())
    }
}

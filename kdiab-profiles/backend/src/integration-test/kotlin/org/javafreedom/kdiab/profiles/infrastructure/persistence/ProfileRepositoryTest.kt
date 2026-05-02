package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.profiles.domain.model.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Repository-level integration tests for [ExposedProfileRepository].
 *
 * Schema is bootstrapped via Liquibase (see [LiquibaseTestHelper]) so that the test
 * database matches production exactly — including database-specific constructs such as
 * the H2 generated column for the one-ACTIVE-per-user constraint.
 *
 * Data is cleared before each test with `DELETE` statements; the schema and the Liquibase
 * tracking table are retained across tests for speed.
 */
@OptIn(ExperimentalUuidApi::class)
class ProfileRepositoryTest {

    private lateinit var repository: ExposedProfileRepository

    companion object {
        // Unique DB name prevents H2 state pollution when test suites run in parallel.
        private val db: Database = LiquibaseTestHelper.setup("test_profile_repo")
    }

    @BeforeTest
    fun setup() {
        LiquibaseTestHelper.cleanData(db)
        repository = ExposedProfileRepository()
    }

    // ── Basic CRUD ─────────────────────────────────────────────────────────────

    @Test
    fun `save should persist profile`() = runBlocking {
        val profile = createTestProfile()
        repository.save(profile)

        val retrieved = repository.findById(profile.id)
        assertNotNull(retrieved)
        assertEquals(profile.id, retrieved.id)
        assertEquals(profile.name, retrieved.name)
        assertEquals(1, retrieved.basal.size)
    }

    @Test
    fun `findAllByUserId should return all profiles for user`() = runBlocking {
        val userId = Uuid.random()
        val p1 = createTestProfile(userId = userId, name = "P1")
        val p2 = createTestProfile(userId = userId, name = "P2")
        val p3 = createTestProfile(userId = Uuid.random(), name = "P3") // Different user

        repository.save(p1)
        repository.save(p2)
        repository.save(p3)

        val results = repository.findAllByUserId(userId)
        assertEquals(2, results.size)
    }

    @Test
    fun `findActiveByUserId should return only active profile`() = runBlocking {
        val userId = Uuid.random()
        val p1 = createTestProfile(userId = userId, status = ProfileStatus.DRAFT)
        val p2 = createTestProfile(userId = userId, status = ProfileStatus.ACTIVE, name = "Active")
        val p3 = createTestProfile(userId = userId, status = ProfileStatus.ARCHIVED)

        repository.save(p1)
        repository.save(p2)
        repository.save(p3)

        val active = repository.findActiveByUserId(userId)
        assertNotNull(active)
        assertEquals("Active", active.name)
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    fun `update should modify profile data and status`() = runBlocking {
        val profile = createTestProfile()
        repository.save(profile)

        val updated = profile.copy(name = "Updated Name", status = ProfileStatus.ACTIVE)
        repository.update(updated)

        val retrieved = repository.findById(profile.id)
        assertNotNull(retrieved)
        assertEquals("Updated Name", retrieved.name)
        assertEquals(ProfileStatus.ACTIVE, retrieved.status)
    }

    @Test
    fun `update should change status without touching immutable profile data`() = runBlocking {
        val profile = createTestProfile(status = ProfileStatus.PROPOSED)
        repository.save(profile)

        // Reject: only status changes, segment data stays the same
        repository.update(profile.copy(status = ProfileStatus.ARCHIVED))

        val retrieved = repository.findById(profile.id)
        assertNotNull(retrieved)
        assertEquals(ProfileStatus.ARCHIVED, retrieved.status)
        assertEquals(profile.name, retrieved.name)
        assertEquals(profile.basal.size, retrieved.basal.size)
    }

    // ── activateProfile ───────────────────────────────────────────────────────

    @Test
    fun `activateProfile should promote DRAFT via status-only update`() = runBlocking {
        val userId = Uuid.random()
        val draft = createTestProfile(userId = userId, status = ProfileStatus.DRAFT)
        repository.save(draft)

        val result = repository.activateProfile(null, draft.copy(status = ProfileStatus.ACTIVE))

        assertEquals(ProfileStatus.ACTIVE, result.status)
        assertEquals(ProfileStatus.ACTIVE, repository.findById(draft.id)?.status)
    }

    @Test
    fun `activateProfile should insert cloned ARCHIVED profile with new id`() = runBlocking {
        val userId = Uuid.random()
        val archived = createTestProfile(userId = userId, status = ProfileStatus.ARCHIVED)
        repository.save(archived)

        val cloned = archived.copy(
            id = Uuid.random(),
            status = ProfileStatus.ACTIVE,
            previousProfileId = archived.id
        )
        val result = repository.activateProfile(null, cloned)

        assertEquals(cloned.id, result.id)
        assertEquals(ProfileStatus.ACTIVE, result.status)
        val retrieved = repository.findById(cloned.id)
        assertNotNull(retrieved)
        assertEquals(archived.id, retrieved.previousProfileId)
        // Original archived row must still exist untouched
        assertEquals(ProfileStatus.ARCHIVED, repository.findById(archived.id)?.status)
    }

    @Test
    fun `activateProfile should archive old active when activating a new profile`() = runBlocking {
        val userId = Uuid.random()
        val currentActive =
            createTestProfile(userId = userId, name = "Current", status = ProfileStatus.ACTIVE)
        val draft = createTestProfile(userId = userId, name = "New", status = ProfileStatus.DRAFT)
        repository.save(currentActive)
        repository.save(draft)

        repository.activateProfile(
            oldActive = currentActive.copy(status = ProfileStatus.ARCHIVED),
            newActive = draft.copy(status = ProfileStatus.ACTIVE)
        )

        assertEquals(ProfileStatus.ARCHIVED, repository.findById(currentActive.id)?.status)
        assertEquals(ProfileStatus.ACTIVE, repository.findById(draft.id)?.status)
    }

    // ── updateActiveProfile (Copy-on-Write) ───────────────────────────────────

    @Test
    fun `updateActiveProfile should archive old and insert new ACTIVE version`() = runBlocking {
        val userId = Uuid.random()
        val active =
            createTestProfile(userId = userId, name = "Original", status = ProfileStatus.ACTIVE)
        repository.save(active)

        val archived = active.copy(status = ProfileStatus.ARCHIVED)
        val newVersion = active.copy(
            id = Uuid.random(),
            name = "Updated",
            status = ProfileStatus.ACTIVE,
            previousProfileId = active.id
        )
        repository.updateActiveProfile(archived, newVersion)

        assertEquals(ProfileStatus.ARCHIVED, repository.findById(active.id)?.status)
        val newRetrieved = repository.findById(newVersion.id)
        assertNotNull(newRetrieved)
        assertEquals("Updated", newRetrieved.name)
        assertEquals(ProfileStatus.ACTIVE, newRetrieved.status)
        assertEquals(active.id, newRetrieved.previousProfileId)
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete should soft-archive the profile`() = runBlocking {
        val profile = createTestProfile(status = ProfileStatus.DRAFT)
        repository.save(profile)

        val deleted = repository.delete(profile.id)

        assertEquals(true, deleted)
        assertEquals(ProfileStatus.ARCHIVED, repository.findById(profile.id)?.status)
    }

    @Test
    fun `deleteByUserIdAndStatus should hard-delete matching profiles only`() = runBlocking {
        val userId = Uuid.random()
        val d1 = createTestProfile(userId = userId, status = ProfileStatus.DRAFT)
        val d2 = createTestProfile(userId = userId, status = ProfileStatus.DRAFT)
        val active = createTestProfile(userId = userId, status = ProfileStatus.ACTIVE)
        repository.save(d1)
        repository.save(d2)
        repository.save(active)

        repository.deleteByUserIdAndStatus(userId, ProfileStatus.DRAFT)

        assertEquals(null, repository.findById(d1.id))
        assertEquals(null, repository.findById(d2.id))
        assertNotNull(repository.findById(active.id))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createTestProfile(
        userId: Uuid = Uuid.random(),
        name: String = "Test Profile",
        status: ProfileStatus = ProfileStatus.DRAFT
    ): Profile =
        Profile(
            userId = userId,
            name = name,
            insulinType = "Fiasp",
            durationOfAction = 180,
            status = status,
            basal = listOf(BasalSegment(LocalTime(0, 0), 0.5)),
            icr = listOf(IcrSegment(LocalTime(0, 0), 15.0)),
            isf = listOf(IsfSegment(LocalTime(0, 0), 40.0)),
            targets = listOf(TargetSegment(LocalTime(0, 0), 100.0, 110.0))
        )
}

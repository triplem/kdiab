@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.profiles.domain.model.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Verifies the DB-level uniqueness constraint that prevents more than one ACTIVE
 * profile per user.
 *
 * The schema is bootstrapped via [LiquibaseTestHelper] so that the database-specific
 * constraint mechanism is exercised:
 * - **PostgreSQL** (production): partial unique index `IDX_PROFILE_STATUSES_USER_ACTIVE`
 *   on `profile_statuses(user_id) WHERE status = 'ACTIVE'`.
 * - **H2** (this test): stored generated column `active_user_key` (equals `user_id`
 *   when `status = 'ACTIVE'`, NULL otherwise) covered by a unique index.
 *   NULL values are not subject to uniqueness, so only ACTIVE rows compete.
 */
class ProfileRepositoryConstraintTest {

    private lateinit var repository: ExposedProfileRepository

    companion object {
        private val db: Database = LiquibaseTestHelper.setup("test_constraint")
    }

    @BeforeTest
    fun setup() {
        repository = ExposedProfileRepository()
        LiquibaseTestHelper.cleanData(db)
    }

    // ── One-ACTIVE-per-user constraint ────────────────────────────────────────

    @Test
    fun `should fail when inserting second active profile for same user`() {
        val userId = Uuid.random()

        transaction {
            insertTestProfileAndStatus(userId, ProfileStatus.ACTIVE)

            assertFailsWith<Exception>("Expected unique-constraint violation for second ACTIVE profile") {
                insertTestProfileAndStatus(userId, ProfileStatus.ACTIVE)
            }
        }
    }

    @Test
    fun `should allow multiple archived profiles for same user`() = runBlocking {
        val userId = Uuid.random()

        repository.save(createTestProfile(userId = userId, status = ProfileStatus.ARCHIVED))
        repository.save(createTestProfile(userId = userId, status = ProfileStatus.ARCHIVED))
        // Must not throw
    }

    @Test
    fun `should allow multiple draft profiles for same user`() = runBlocking {
        val userId = Uuid.random()

        repository.save(createTestProfile(userId = userId, status = ProfileStatus.DRAFT))
        repository.save(createTestProfile(userId = userId, status = ProfileStatus.DRAFT))
        // Must not throw
    }

    @Test
    fun `should allow one active per user and additional active for different users`() = runBlocking {
        val user1 = Uuid.random()
        val user2 = Uuid.random()

        repository.save(createTestProfile(userId = user1, status = ProfileStatus.ACTIVE))
        repository.save(createTestProfile(userId = user2, status = ProfileStatus.ACTIVE))
        // Two different users — must not throw
    }

    // ── Status transitions via repository ────────────────────────────────────

    @Test
    fun `activateProfile should atomically archive old and activate new`() = runBlocking {
        val userId = Uuid.random()
        val currentActive = createTestProfile(userId = userId, status = ProfileStatus.ACTIVE)
        val draft = createTestProfile(userId = userId, status = ProfileStatus.DRAFT)

        repository.save(currentActive)
        repository.save(draft)

        // Atomic swap — must not trigger the unique constraint
        repository.activateProfile(
            oldActive = currentActive.copy(status = ProfileStatus.ARCHIVED),
            newActive = draft.copy(status = ProfileStatus.ACTIVE)
        )

        val nowActive = repository.findActiveByUserId(userId)
        kotlin.test.assertNotNull(nowActive)
        kotlin.test.assertEquals(draft.id, nowActive.id)
        kotlin.test.assertEquals(ProfileStatus.ACTIVE, nowActive.status)
        kotlin.test.assertEquals(
            ProfileStatus.ARCHIVED,
            repository.findById(currentActive.id)?.status
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Low-level helper that inserts directly into both tables within the caller's
     * transaction, bypassing the repository abstraction to trigger DB-level constraints
     * deterministically.
     */
    private fun insertTestProfileAndStatus(userId: Uuid, status: ProfileStatus) {
        val profileId = Uuid.random()
        Profiles.insert {
            it[Profiles.id] = profileId
            it[Profiles.userId] = userId
            it[name] = "Test Profile"
            it[insulinType] = "Fiasp"
            it[units] = "mg/dl"
            it[durationOfAction] = 180
            it[timeZone] = "UTC"
            it[createdAt] = java.time.Instant.now()
            it[segments] = ProfileSegments(
                basal = listOf(BasalSegment(LocalTime(0, 0), 1.0))
            )
        }
        ProfileStatuses.insert {
            it[ProfileStatuses.profileId] = profileId
            it[ProfileStatuses.userId] = userId
            it[ProfileStatuses.status] = status
            it[validFrom] = java.time.Instant.now()
        }
    }

    private fun createTestProfile(
        userId: Uuid = Uuid.random(),
        status: ProfileStatus = ProfileStatus.DRAFT
    ): Profile =
        Profile(
            userId = userId,
            name = "Test Profile",
            insulinType = "Fiasp",
            durationOfAction = 180,
            status = status,
            basal = listOf(BasalSegment(LocalTime(0, 0), 0.5)),
            icr = emptyList(),
            isf = emptyList(),
            targets = emptyList()
        )
}

@file:Suppress("WildcardImport", "MagicNumber", "MaxLineLength", "TooManyFunctions")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.infrastructure.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.common.domain.SQL_UNIQUE_VIOLATION
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.profiles.domain.model.*
import org.javafreedom.kdiab.profiles.domain.repository.ProfileRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.json.jsonb

private val logger = KotlinLogging.logger {}

@Serializable
data class ProfileSegments(
    val basal: List<BasalSegment> = emptyList(),
    val icr: List<IcrSegment> = emptyList(),
    val isf: List<IsfSegment> = emptyList(),
    val targets: List<TargetSegment> = emptyList()
)

/**
 * Immutable profile data. Status is tracked separately in [ProfileStatuses].
 * Once written, rows in this table are never mutated -- copy-on-write semantics
 * ensure a new row is created on every effective change to an active profile.
 */
object Profiles : Table("profiles") {
    private const val NAME_LENGTH = 255
    private const val INSULIN_TYPE_LENGTH = 100
    private const val UNITS_LENGTH = 20

    val id = uuid("id")
    val userId = uuid("user_id")
    val previousProfileId = uuid("previous_profile_id").references(id).nullable()
    val name = varchar("name", NAME_LENGTH)
    val insulinType = varchar("insulin_type", INSULIN_TYPE_LENGTH)
    val units = varchar("units", UNITS_LENGTH).default("mg/dL")
    val durationOfAction = integer("duration_of_action")
    val timeZone = varchar("time_zone", 50)
    val createdAt = timestamp("created_at")
    val proposalReason = text("proposal_reason").nullable()
    val createdBy = uuid("created_by").nullable()
    val rejectionReason = text("rejection_reason").nullable()
    val analysisLow = double("analysis_low").nullable()
    val analysisHigh = double("analysis_high").nullable()
    val carbAbsorptionRateGPerHour = double("carb_absorption_rate_g_per_hour").nullable()
    val segments = jsonb<ProfileSegments>("segments", Json.Default)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Mutable lifecycle state for each profile. A profile transitions through
 * DRAFT → ACTIVE → ARCHIVED (or PROPOSED → ACTIVE/ARCHIVED for doctor-created
 * profiles) by updating this table only, leaving [Profiles] untouched.
 *
 * One-ACTIVE-per-user enforcement:
 * - **PostgreSQL** (production): partial unique index `IDX_PROFILE_STATUSES_USER_ACTIVE`
 *   on `user_id WHERE status = 'ACTIVE'`.
 * - **H2** (integration tests): stored generated column `active_user_key` that
 *   equals `user_id` when `status = 'ACTIVE'` and `NULL` otherwise, combined with
 *   a unique index. NULL values are not constrained, so only ACTIVE rows compete.
 */
object ProfileStatuses : Table("profile_statuses") {
    val profileId = uuid("profile_id").references(Profiles.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id")
    val status = enumerationByName("status", 50, ProfileStatus::class)
    val validFrom = timestamp("valid_from")
    val activatedAt = timestamp("activated_at").nullable()
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(profileId)
}

class ExposedProfileRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProfileRepository {

    // -- Reads -----------------------------------------------------------------

    override suspend fun findById(id: Uuid): Profile? = withContext(ioDispatcher) {
        suspendTransaction {
            (Profiles innerJoin ProfileStatuses).selectAll()
                .where { Profiles.id eq id }
                .map { mapToProfile(it) }
                .singleOrNull()
        }
    }

    override suspend fun findAllByUserId(userId: Uuid, page: Int, size: Int): List<Profile> = withContext(ioDispatcher) {
        suspendTransaction {
            (Profiles innerJoin ProfileStatuses).selectAll()
                .where { Profiles.userId eq userId }
                .orderBy(Profiles.createdAt, SortOrder.DESC)
                .limit(size)
                .offset(page.toLong() * size.toLong())
                .map { mapToProfile(it) }
        }
    }

    override suspend fun countByUserId(userId: Uuid): Long = withContext(ioDispatcher) {
        suspendTransaction {
            (Profiles innerJoin ProfileStatuses).selectAll()
                .where { Profiles.userId eq userId }
                .count()
        }
    }

    override suspend fun findByStatuses(
        userId: Uuid,
        statuses: List<ProfileStatus>,
        page: Int,
        size: Int
    ): List<Profile> = withContext(ioDispatcher) {
        suspendTransaction {
            (Profiles innerJoin ProfileStatuses).selectAll()
                .where {
                    (Profiles.userId eq userId) and
                        (ProfileStatuses.status inList statuses)
                }
                .orderBy(Profiles.createdAt, SortOrder.DESC)
                .limit(size)
                .offset(page.toLong() * size.toLong())
                .map { mapToProfile(it) }
        }
    }

    override suspend fun countByStatuses(userId: Uuid, statuses: List<ProfileStatus>): Long =
        withContext(ioDispatcher) {
            suspendTransaction {
                (Profiles innerJoin ProfileStatuses).selectAll()
                    .where {
                        (Profiles.userId eq userId) and
                            (ProfileStatuses.status inList statuses)
                    }
                    .count()
            }
        }

    override suspend fun findActiveByUserId(userId: Uuid): Profile? = withContext(ioDispatcher) {
        suspendTransaction {
            (Profiles innerJoin ProfileStatuses).selectAll()
                .where {
                    (Profiles.userId eq userId) and
                        (ProfileStatuses.status eq ProfileStatus.ACTIVE)
                }
                .map { mapToProfile(it) }
                .singleOrNull()
        }
    }

    override suspend fun findHistory(
        userId: Uuid,
        from: Instant,
        to: Instant
    ): List<Profile> = withContext(ioDispatcher) {
        suspendTransaction {
            (Profiles innerJoin ProfileStatuses).selectAll()
                .where {
                    (Profiles.userId eq userId) and
                        (ProfileStatuses.status eq ProfileStatus.ARCHIVED) and
                        (Profiles.createdAt greaterEq from) and
                        (Profiles.createdAt lessEq to)
                }
                .orderBy(Profiles.createdAt, SortOrder.DESC)
                .map { mapToProfile(it) }
        }
    }

    override suspend fun existsActiveOrDraftWithName(userId: Uuid, name: String, excludeId: Uuid?): Boolean =
        withContext(ioDispatcher) {
            suspendTransaction {
                val query = (Profiles innerJoin ProfileStatuses)
                    .select(Profiles.id)
                    .where {
                        (ProfileStatuses.userId eq userId) and
                            (Profiles.name eq name) and
                            (ProfileStatuses.status neq ProfileStatus.ARCHIVED)
                    }
                if (excludeId != null) {
                    query.andWhere { Profiles.id neq excludeId }
                }
                query.limit(1).count() > 0
            }
        }

    // -- Writes ----------------------------------------------------------------

    override suspend fun save(profile: Profile): Profile = withContext(ioDispatcher) {
        suspendTransaction {
            insertProfileInTx(profile)
            insertStatusInTx(profile)
            profile
        }
    }

    /**
     * Updates profile data and status in a single transaction.
     * Profile data is written to [Profiles]; status is written to [ProfileStatuses].
     * For pure status-only transitions (reject, archive) the data UPDATE is a no-op
     * at the DB level but keeps the call site uniform.
     */
    override suspend fun update(profile: Profile): Profile = withContext(ioDispatcher) {
        suspendTransaction { updateProfileInTx(profile) }
    }

    /**
     * Soft-deletes a profile by archiving it (status → ARCHIVED).
     * Only the [ProfileStatuses] row is touched; profile data is preserved.
     */
    override suspend fun delete(id: Uuid): Boolean = withContext(ioDispatcher) {
        suspendTransaction {
            ProfileStatuses.update({ ProfileStatuses.profileId eq id }) {
                it[status] = ProfileStatus.ARCHIVED
                it[validFrom] = Clock.System.now()
            } > 0
        }
    }

    /**
     * Hard-deletes all profiles (and their status rows via CASCADE) matching
     * [userId] and [status]. Used exclusively for bulk DRAFT cleanup.
     */
    override suspend fun deleteByUserIdAndStatus(
        userId: Uuid,
        status: ProfileStatus
    ): Boolean = withContext(ioDispatcher) {
        suspendTransaction {
            val profileIds = ProfileStatuses.selectAll()
                .where {
                    (ProfileStatuses.userId eq userId) and
                        (ProfileStatuses.status eq status)
                }
                .map { it[ProfileStatuses.profileId] }

            if (profileIds.isEmpty()) return@suspendTransaction false

            // ON DELETE CASCADE on the FK removes the profile_statuses rows automatically.
            Profiles.deleteWhere { Profiles.id inList profileIds } > 0
        }
    }

    /**
     * Copy-on-Write update for ACTIVE profiles.
     * Archives the old profile (status-only change) and inserts the new version
     * as a fresh profile + status row -- both in the same transaction.
     */
    override suspend fun updateActiveProfile(oldProfile: Profile, newProfile: Profile): Profile =
        withContext(ioDispatcher) {
            suspendTransaction {
                updateStatusInTx(oldProfile.id, ProfileStatus.ARCHIVED, archivedAt = oldProfile.archivedAt)
                insertProfileInTx(newProfile)
                insertStatusInTx(newProfile)
                newProfile
            }
        }

    /**
     * Atomically archives the current active profile (if any) and activates
     * [newActive]. If [newActive] already has a row in [Profiles] (i.e. a DRAFT
     * being promoted), only its status entry is updated. If it is a freshly
     * cloned profile (ARCHIVED → ACTIVE clone), both rows are inserted.
     *
     * The DB unique constraint (PostgreSQL partial index / H2 generated column)
     * ensures at most one ACTIVE profile per user and will throw on concurrent
     * activation races -- the service layer converts that to a 409 Conflict.
     */
    override suspend fun activateProfile(oldActive: Profile?, newActive: Profile): Profile =
        withContext(ioDispatcher) {
            try {
                suspendTransaction {
                    oldActive?.let { updateStatusInTx(it.id, ProfileStatus.ARCHIVED, archivedAt = it.archivedAt) }

                    val exists = Profiles.selectAll()
                        .where { Profiles.id eq newActive.id }
                        .count() > 0

                    if (exists) {
                        updateStatusInTx(newActive.id, ProfileStatus.ACTIVE, activatedAt = newActive.activatedAt)
                    } else {
                        insertProfileInTx(newActive)
                        insertStatusInTx(newActive)
                    }

                    newActive
                }
            } catch (e: org.jetbrains.exposed.v1.exceptions.ExposedSQLException) {
                val sqlState = e.cause?.let { (it as? java.sql.SQLException)?.sqlState }
                if (sqlState == SQL_UNIQUE_VIOLATION) {
                    throw ConflictException(
                        "Only one profile can be active at a time -- deactivate the current active profile first.",
                        e
                    )
                }
                throw e
            }
        }

    // -- Private helpers -------------------------------------------------------

    private fun insertProfileInTx(profile: Profile) {
        Profiles.insert {
            it[Profiles.id] = profile.id
            it[userId] = profile.userId
            it[previousProfileId] = profile.previousProfileId
            it[name] = profile.name
            it[insulinType] = profile.insulinType
            it[units] = profile.units
            it[durationOfAction] = profile.durationOfAction
            it[timeZone] = profile.timeZone.id
            it[createdAt] = profile.createdAt
            it[proposalReason] = profile.proposalReason
            it[createdBy] = profile.createdBy
            it[rejectionReason] = profile.rejectionReason
            it[analysisLow] = profile.analysisLow
            it[analysisHigh] = profile.analysisHigh
            it[carbAbsorptionRateGPerHour] = profile.carbAbsorptionRateGPerHour
            it[segments] = ProfileSegments(
                basal = profile.basal,
                icr = profile.icr,
                isf = profile.isf,
                targets = profile.targets
            )
        }
    }

    private fun insertStatusInTx(profile: Profile) {
        ProfileStatuses.insert {
            it[profileId] = profile.id
            it[userId] = profile.userId
            it[status] = profile.status
            it[validFrom] = Clock.System.now()
            it[activatedAt] = profile.activatedAt
            it[archivedAt] = profile.archivedAt
        }
    }

    private fun updateProfileInTx(profile: Profile): Profile {
        Profiles.update({ Profiles.id eq profile.id }) {
            it[previousProfileId] = profile.previousProfileId
            it[name] = profile.name
            it[insulinType] = profile.insulinType
            it[units] = profile.units
            it[durationOfAction] = profile.durationOfAction
            it[timeZone] = profile.timeZone.id
            it[rejectionReason] = profile.rejectionReason
            it[analysisLow] = profile.analysisLow
            it[analysisHigh] = profile.analysisHigh
            it[carbAbsorptionRateGPerHour] = profile.carbAbsorptionRateGPerHour
            it[segments] = ProfileSegments(
                basal = profile.basal,
                icr = profile.icr,
                isf = profile.isf,
                targets = profile.targets
            )
        }
        updateStatusInTx(profile.id, profile.status, profile.activatedAt, profile.archivedAt)
        return profile
    }

    private fun updateStatusInTx(
        profileId: Uuid,
        newStatus: ProfileStatus,
        activatedAt: kotlin.time.Instant? = null,
        archivedAt: kotlin.time.Instant? = null
    ) {
        ProfileStatuses.update({ ProfileStatuses.profileId eq profileId }) {
            it[status] = newStatus
            it[validFrom] = Clock.System.now()
            if (activatedAt != null) it[ProfileStatuses.activatedAt] = activatedAt
            if (archivedAt != null) it[ProfileStatuses.archivedAt] = archivedAt
        }
    }

    private fun mapToProfile(row: ResultRow): Profile {
        val pSectors: ProfileSegments = row[Profiles.segments]
        val tz = try {
            TimeZone.of(row[Profiles.timeZone])
        } catch (e: IllegalTimeZoneException) {
            logger.warn(e) {
                "Unknown timezone '${row[Profiles.timeZone]}' for profile ${row[Profiles.id]}, falling back to UTC"
            }
            TimeZone.UTC
        }
        return Profile(
            id = row[Profiles.id],
            userId = row[Profiles.userId],
            previousProfileId = row[Profiles.previousProfileId],
            name = row[Profiles.name],
            insulinType = row[Profiles.insulinType],
            units = row[Profiles.units],
            durationOfAction = row[Profiles.durationOfAction],
            timeZone = tz,
            status = row[ProfileStatuses.status],
            createdAt = row[Profiles.createdAt],
            validFrom = row[ProfileStatuses.validFrom],
            activatedAt = row[ProfileStatuses.activatedAt],
            archivedAt = row[ProfileStatuses.archivedAt],
            proposalReason = row[Profiles.proposalReason],
            createdBy = row[Profiles.createdBy],
            rejectionReason = row[Profiles.rejectionReason],
            analysisLow = row[Profiles.analysisLow],
            analysisHigh = row[Profiles.analysisHigh],
            carbAbsorptionRateGPerHour = row[Profiles.carbAbsorptionRateGPerHour],
            basal = pSectors.basal,
            icr = pSectors.icr,
            isf = pSectors.isf,
            targets = pSectors.targets
        )
    }
}

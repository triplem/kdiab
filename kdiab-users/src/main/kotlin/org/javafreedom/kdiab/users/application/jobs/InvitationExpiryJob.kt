package org.javafreedom.kdiab.users.application.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.javafreedom.kdiab.users.application.service.InvitationService

private val logger = KotlinLogging.logger {}

/**
 * Background job that periodically expires PENDING invitations whose `expires_at` timestamp
 * is in the past.
 *
 * The job is started once via [start] and runs until the coroutine scope is cancelled (i.e.
 * when the Ktor application stops). If the repository call throws, the exception is caught and
 * logged as ERROR so that the job reschedules its next run regardless of transient failures.
 *
 * @param invitationService the service that performs the bulk-expiry operation.
 * @param intervalMs how often the job runs in milliseconds. Default is 1 hour.
 *   Configurable via `INVITATION_EXPIRY_INTERVAL_MINUTES` env var / `app.invitationExpiryIntervalMinutes` config.
 */
class InvitationExpiryJob(
    private val invitationService: InvitationService,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        const val DEFAULT_INTERVAL_MINUTES = 60L
        const val DEFAULT_INTERVAL_MS = DEFAULT_INTERVAL_MINUTES * MILLIS_PER_MINUTE
    }

    /**
     * Launches the expiry loop in [scope]. The coroutine is a child of [scope], so it is
     * automatically cancelled when the scope is cancelled.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            logger.info { "invitation_expiry_job started intervalMs=$intervalMs" }
            while (isActive) {
                runCatching { invitationService.expireOldInvitations() }
                    .onFailure { logger.error(it) { "invitation_expiry_job failed" } }
                delay(intervalMs)
            }
            logger.info { "invitation_expiry_job stopped" }
        }
    }
}

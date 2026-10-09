package dev.fajar.hris.communications.data.repositories

import dev.fajar.hris.communications.data.datasources.InboxPushDataSource
import dev.fajar.hris.communications.data.mappers.*
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.InboxPushRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import java.time.Instant
import java.util.UUID

class StoredInboxPushRepository(private val source: InboxPushDataSource) : InboxPushRepository {
    override fun claim(owner: UUID, limit: Int, leaseSeconds: Int, maximumAttempts: Int) =
        safeDatabaseCall {
            source.claim(owner, limit, leaseSeconds, maximumAttempts).map { it.toInboxPushLease() }
        }

    override fun lock(lease: InboxPushLease) = safeDatabaseCall {
        source
            .lock(lease.dispatch.companyId, lease.dispatch.inboxId, lease.owner, lease.token)
            ?.toInboxPushDispatch()
    }

    override fun pin(lease: InboxPushLease, sessionId: UUID) = safeDatabaseCall {
        source.pin(
            lease.dispatch.companyId,
            lease.dispatch.inboxId,
            lease.owner,
            lease.token,
            sessionId,
        )
    }

    override fun advance(
        lease: InboxPushLease,
        sessionId: UUID,
        outcome: InboxPushTargetOutcome,
        code: String?,
        now: Instant,
    ) = safeDatabaseCall {
        source.advance(
            lease.dispatch.companyId,
            lease.dispatch.inboxId,
            lease.owner,
            lease.token,
            sessionId,
            outcome == InboxPushTargetOutcome.ACCEPTED,
            outcome == InboxPushTargetOutcome.REJECTED,
            code,
            now,
        )
    }

    override fun retry(lease: InboxPushLease, availableAt: Instant, code: String) =
        safeDatabaseCall {
            source.retry(
                lease.dispatch.companyId,
                lease.dispatch.inboxId,
                lease.owner,
                lease.token,
                availableAt,
                code,
            )
        }

    override fun finish(lease: InboxPushLease, state: InboxPushState, code: String?, now: Instant) =
        safeDatabaseCall {
            source.finish(
                lease.dispatch.companyId,
                lease.dispatch.inboxId,
                lease.owner,
                lease.token,
                state.name,
                code,
                now,
            )
        }

    override fun exhausted(olderThan: Instant, maximumAttempts: Int, limit: Int) =
        safeDatabaseCall {
            source.exhausted(olderThan, maximumAttempts, limit).map { it.toInboxPushDispatch() }
        }

    override fun failUnleased(observed: InboxPushDispatch, code: String, now: Instant) =
        safeDatabaseCall {
            source.failUnleased(
                observed.companyId,
                observed.inboxId,
                observed.processedCount,
                observed.attempts,
                code,
                now,
            )
        }

    override fun purgeFinished(before: Instant, limit: Int) = safeDatabaseCall {
        source.purgeFinished(before, limit)
    }
}

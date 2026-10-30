package dev.fajar.hris.communications.data.repositories

import dev.fajar.hris.communications.data.datasources.InboxEmailDataSource
import dev.fajar.hris.communications.data.mappers.*
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.InboxEmailRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import java.time.Instant
import java.util.UUID

class StoredInboxEmailRepository(private val source: InboxEmailDataSource) : InboxEmailRepository {
    override fun claim(owner: UUID, limit: Int, leaseSeconds: Int, maximumAttempts: Int) =
        safeDatabaseCall {
            source.claim(owner, limit, leaseSeconds, maximumAttempts).map { it.toInboxEmailLease() }
        }

    override fun lock(lease: InboxEmailLease) = safeDatabaseCall {
        source
            .lock(lease.dispatch.companyId, lease.dispatch.inboxId, lease.owner, lease.token)
            ?.toInboxEmailDispatch()
    }

    override fun retry(lease: InboxEmailLease, availableAt: Instant, code: String) =
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

    override fun finish(
        lease: InboxEmailLease,
        state: InboxEmailState,
        code: String?,
        now: Instant,
    ) = safeDatabaseCall {
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
            source.exhausted(olderThan, maximumAttempts, limit).map { it.toInboxEmailDispatch() }
        }

    override fun failUnleased(observed: InboxEmailDispatch, code: String, now: Instant) =
        safeDatabaseCall {
            source.failUnleased(observed.companyId, observed.inboxId, observed.attempts, code, now)
        }

    override fun purgeFinished(before: Instant, limit: Int) = safeDatabaseCall {
        source.purgeFinished(before, limit)
    }
}

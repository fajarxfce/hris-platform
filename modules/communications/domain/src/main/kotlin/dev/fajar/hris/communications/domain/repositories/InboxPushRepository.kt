package dev.fajar.hris.communications.domain.repositories

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

interface InboxPushRepository {
    fun claim(
        owner: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): Result<List<InboxPushLease>>

    fun lock(lease: InboxPushLease): Result<InboxPushDispatch?>

    fun pin(lease: InboxPushLease, sessionId: UUID): Result<Boolean>

    fun advance(
        lease: InboxPushLease,
        sessionId: UUID,
        outcome: InboxPushTargetOutcome,
        code: String?,
        now: Instant,
    ): Result<Boolean>

    fun retry(lease: InboxPushLease, availableAt: Instant, code: String): Result<Boolean>

    fun finish(
        lease: InboxPushLease,
        state: InboxPushState,
        code: String?,
        now: Instant,
    ): Result<Boolean>

    fun exhausted(
        olderThan: Instant,
        maximumAttempts: Int,
        limit: Int,
    ): Result<List<InboxPushDispatch>>

    fun failUnleased(observed: InboxPushDispatch, code: String, now: Instant): Result<Boolean>

    fun purgeFinished(before: Instant, limit: Int): Result<Int>
}

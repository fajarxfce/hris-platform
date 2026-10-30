package dev.fajar.hris.communications.domain.repositories

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

interface InboxEmailRepository {
    fun claim(
        owner: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): Result<List<InboxEmailLease>>

    fun lock(lease: InboxEmailLease): Result<InboxEmailDispatch?>

    fun retry(lease: InboxEmailLease, availableAt: Instant, code: String): Result<Boolean>

    fun finish(
        lease: InboxEmailLease,
        state: InboxEmailState,
        code: String?,
        now: Instant,
    ): Result<Boolean>

    fun exhausted(
        olderThan: Instant,
        maximumAttempts: Int,
        limit: Int,
    ): Result<List<InboxEmailDispatch>>

    fun failUnleased(observed: InboxEmailDispatch, code: String, now: Instant): Result<Boolean>

    fun purgeFinished(before: Instant, limit: Int): Result<Int>
}

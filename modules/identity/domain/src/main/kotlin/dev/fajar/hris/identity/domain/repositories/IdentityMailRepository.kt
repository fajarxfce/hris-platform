package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant
import java.util.UUID

interface IdentityMailRepository {
    fun enqueue(link: IssuedCredentialLink): Result<Unit>

    fun supersedePending(accountId: UUID, at: Instant): Result<Unit>

    fun find(challengeId: UUID): Result<IdentityMailDelivery?>

    fun exhausted(
        now: Instant,
        maximumAttempts: Int,
        limit: Int,
    ): Result<List<IdentityMailDelivery>>

    fun failUnleased(challengeId: UUID, now: Instant, code: String): Result<Boolean>

    fun claim(
        owner: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): Result<List<IdentityMailLease>>

    fun lock(lease: IdentityMailLease): Result<IdentityMailDelivery?>

    fun token(lease: IdentityMailLease): Result<String>

    fun finish(lease: IdentityMailLease, outcome: IdentityMailOutcome, at: Instant): Result<Boolean>
}

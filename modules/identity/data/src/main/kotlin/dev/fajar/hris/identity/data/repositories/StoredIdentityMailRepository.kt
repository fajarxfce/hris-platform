package dev.fajar.hris.identity.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.mappers.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.IdentityMailRepository
import dev.fajar.hris.schema.tables.records.IdentityMailDeliveriesRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

class StoredIdentityMailRepository(
    private val source: IdentityMailDataSource,
    private val tokens: IdentityTokenDataSource,
) : IdentityMailRepository {
    override fun enqueue(link: IssuedCredentialLink): Result<Unit> = safeIdentityCall {
        val challenge = link.challenge
        source.insert(
            IdentityMailDeliveriesRecord().also {
                it.challengeId = challenge.id
                it.accountId = challenge.accountId
                it.createdBy = challenge.createdBy
                it.availableAt = challenge.issuedAt.atOffset(UTC)
                it.expiresAt = challenge.expiresAt.atOffset(UTC)
                it.tokenEncrypted =
                    tokens.encrypt(
                        "credential-mail:${challenge.accountId}:${challenge.id}",
                        link.token,
                    )
            }
        )
    }

    override fun supersedePending(accountId: UUID, at: Instant): Result<Unit> = safeDatabaseCall {
        source.supersedePending(accountId, at)
    }

    override fun find(challengeId: UUID): Result<IdentityMailDelivery?> = safeDatabaseCall {
        source.find(challengeId)?.toIdentityMailDelivery()
    }

    override fun exhausted(
        now: Instant,
        maximumAttempts: Int,
        limit: Int,
    ): Result<List<IdentityMailDelivery>> = safeDatabaseCall {
        source.exhausted(now, maximumAttempts, limit).map { it.toIdentityMailDelivery() }
    }

    override fun failUnleased(challengeId: UUID, now: Instant, code: String): Result<Boolean> =
        safeDatabaseCall {
            source.failUnleased(challengeId, now, code)
        }

    override fun claim(
        owner: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): Result<List<IdentityMailLease>> = safeDatabaseCall {
        source.claim(owner, limit, leaseSeconds, maximumAttempts).map { it.toIdentityMailLease() }
    }

    override fun lock(lease: IdentityMailLease): Result<IdentityMailDelivery?> = safeDatabaseCall {
        source.lock(lease.challengeId, lease.owner, lease.token)?.toIdentityMailDelivery()
    }

    override fun token(lease: IdentityMailLease): Result<String> =
        safeIdentityCall {
                source.token(lease.challengeId, lease.owner, lease.token)?.let {
                    tokens.decrypt("credential-mail:${lease.accountId}:${lease.challengeId}", it)
                }
            }
            .flatMap {
                if (it != null) Result.Success(it)
                else Result.Failed(Failure(FailureKind.CONFLICT, "mail_lease_lost"))
            }

    override fun finish(
        lease: IdentityMailLease,
        outcome: IdentityMailOutcome,
        at: Instant,
    ): Result<Boolean> = safeDatabaseCall {
        source.finish(
            lease.challengeId,
            lease.owner,
            lease.token,
            outcome.state.name,
            outcome.availableAt,
            outcome.failureCode,
            outcome.discardToken,
            at,
        )
    }
}

package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class IssueNativeSession(
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val clock: Clock,
    private val policy: NativeSessionPolicy,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        deviceName: String,
        purpose: NativeSessionPurpose = NativeSessionPurpose.VERIFIED_EXCHANGE,
    ): Result<NativeTokens> {
        val name = deviceName.trim()
        if (name.length !in 1..100 || name.any { it.isISOControl() })
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_device_name"))
        return transactions.run(actor.copy(companyId = null)) {
            val lock = sessions.lockAccount(actor.accountId)
            if (lock is Result.Failed) return@run lock
            val account = (lock as Result.Success).value
            if (
                account == null ||
                    !account.active ||
                    account.securityVersion != actor.credentialVersion
            )
                return@run nativeAuthenticationRequired()
            val now = clock.instant()
            val recent = requireRecentAuthentication(actor, now, security.recentAuthenticationAge)
            if (recent is Result.Failed) return@run recent
            val access = identities.access(actor.accountId, null)
            if (access is Result.Failed) return@run access
            val current =
                (access as Result.Success).value ?: return@run nativeAuthenticationRequired()
            val assurance =
                validateSessionAssurance(
                    account,
                    current.securityPermissions,
                    actor.mfaVerifiedAt,
                    now,
                    security,
                )
            // A freshly verified password can create a pending native session. Every business
            // request still passes ResolveActor's live MFA gate; the browser exchange must
            // already satisfy that gate. A request cannot select its own issuance purpose.
            if (purpose == NativeSessionPurpose.VERIFIED_EXCHANGE && assurance is Result.Failed)
                return@run assurance
            val existing = sessions.findExchange(actor.accountId, operationId)
            if (existing is Result.Failed) return@run existing
            val previous = (existing as Result.Success).value
            if (previous != null) {
                if (previous.deviceName != name)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "operation_payload_mismatch")
                    )
                if (
                    !nativeSessionActive(previous, now) ||
                        previous.credentialVersion != account.securityVersion ||
                        previous.version != 0L ||
                        previous.exchangeReplayUntil?.isAfter(now) != true
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "native_exchange_expired")
                    )
                return@run sessions.replayExchange(previous).flatMap {
                    if (it == null)
                        Result.Failed(Failure(FailureKind.CONFLICT, "native_exchange_expired"))
                    else Result.Success(it)
                }
            }
            val cleanup = sessions.purgeExpired(actor.accountId, now.minusSeconds(86400), 100)
            if (cleanup is Result.Failed) return@run cleanup
            val existingSessions = sessions.list(actor.accountId, now, policy.maximumSessions + 1)
            if (existingSessions is Result.Failed) return@run existingSessions
            if ((existingSessions as Result.Success).value.size >= policy.maximumSessions)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "native_session_limit"))
            val issued = sessions.countIssued(actor.accountId, now)
            if (issued is Result.Failed) return@run issued
            if ((issued as Result.Success).value >= policy.maximumIssuedSessions)
                return@run Result.Failed(
                    Failure(FailureKind.RATE_LIMITED, "native_session_creation_limit")
                )
            val session =
                NativeSession(
                    UUID.randomUUID(),
                    actor.accountId,
                    name,
                    now,
                    actor.authenticatedAt,
                    actor.mfaVerifiedAt,
                    account.securityVersion,
                    now.plus(policy.sessionLifetime),
                    now.plus(policy.accessLifetime),
                    now,
                    null,
                    0,
                    operationId,
                    now.plus(policy.replayLifetime),
                )
            sessions.create(session, now.plus(policy.replayLifetime)).flatMap { tokens ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "native_session",
                            session.id,
                            "identity.native_session_created",
                        ),
                    )
                    .map { tokens }
            }
        }
    }
}

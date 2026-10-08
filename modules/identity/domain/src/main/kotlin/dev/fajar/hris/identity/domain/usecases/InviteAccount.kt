package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class InviteAccount(
    private val accounts: AccountAdministrationRepository,
    private val credentials: CredentialChallengeRepository,
    private val mail: IdentityMailRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val policy: CredentialChallengePolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        email: String,
        displayName: String,
        reason: String,
        expectedVersion: Long?,
    ): Result<MutationReceipt> {
        if (actor.companyId != null || "identity.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_administrator_required"))
        val recent =
            if (security.enforceMfa)
                requireRecentMfa(actor, clock.instant(), security.recentAuthenticationAge)
            else
                requireRecentAuthentication(
                    actor,
                    clock.instant(),
                    security.recentAuthenticationAge,
                )
        if (recent is Result.Failed) return recent
        val normalized = email.trim().lowercase()
        val name = displayName.trim()
        val valid = validateInvitation(normalized, name, reason, expectedVersion)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "identity.account_invite",
                operationId,
                listOf(id.toString(), normalized, name, reason, expectedVersion.toString()),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (!policy.enabled)
                return@run Result.Failed(Failure(FailureKind.UNAVAILABLE, "mail_not_configured"))
            val administration = accounts.lockAdministration()
            if (administration is Result.Failed) return@run administration
            val author = accounts.lockAccount(actor.accountId)
            if (author is Result.Failed) return@run author
            val currentAuthor = (author as Result.Success).value
            if (
                currentAuthor == null ||
                    !currentAuthor.account.active ||
                    "identity.manage" !in currentAuthor.platformPermissions ||
                    (actor.credentialVersion != null &&
                        actor.credentialVersion != currentAuthor.account.securityVersion)
            )
                return@run Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
            val found = credentials.lockAccount(id)
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            val changed =
                if (current == null) {
                    if (expectedVersion != null)
                        return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                    credentials.createPendingAccount(id, normalized, name)
                } else {
                    if (current.active || current.hasPassword || current.email != normalized)
                        return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "account_cannot_be_invited")
                        )
                    if (current.version != expectedVersion)
                        return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                    credentials.renewInvitation(id, current.version, name)
                }
            if (changed is Result.Failed) return@run changed
            val account = (changed as Result.Success).value
            val now = clock.instant()
            val challenge =
                CredentialChallenge(
                    UUID.randomUUID(),
                    id,
                    CredentialChallengeKind.INVITATION,
                    account.securityVersion,
                    actor.accountId,
                    now,
                    now.plusSeconds(policy.invitationSeconds),
                    null,
                    null,
                )
            val receipt = MutationReceipt(id, account.version)
            credentials
                .revokePending(id, now)
                .flatMap { mail.supersedePending(id, now) }
                .flatMap { credentials.issue(challenge) }
                .flatMap { mail.enqueue(it) }
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "account",
                            id,
                            "identity.account_invited",
                            mapOf("challengeId" to challenge.id.toString()),
                            reason,
                        ),
                    )
                }
                .map { receipt }
        }
    }
}

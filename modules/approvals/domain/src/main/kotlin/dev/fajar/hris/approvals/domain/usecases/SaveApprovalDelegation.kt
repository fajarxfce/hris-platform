package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.approvalPermissions
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.Duration
import java.util.UUID

class SaveApprovalDelegation(
    private val approvals: ApprovalRepository,
    private val members: MembershipRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        delegation: Delegation,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("approvals.read")
        if (access is Result.Failed) return access
        if (delegation.fromAccount != actor.accountId && "approvals.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (
            delegation.fromAccount == delegation.toAccount ||
                !delegation.validUntil.isAfter(delegation.validFrom) ||
                Duration.between(delegation.validFrom, delegation.validUntil) >
                    Duration.ofDays(90) ||
                (expectedVersion ?: 0) < 0 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_delegation"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "approvals.delegation_save",
                operationId,
                listOf(
                    delegation.id.toString(),
                    delegation.kind.name,
                    delegation.fromAccount.toString(),
                    delegation.toAccount.toString(),
                    delegation.validFrom.toString(),
                    delegation.validUntil.toString(),
                    delegation.active.toString(),
                    expectedVersion?.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            for (account in
                setOf(actor.accountId, delegation.fromAccount, delegation.toAccount).sorted()) {
                val accountLock = identities.lockAccount(account)
                if (accountLock is Result.Failed) return@run accountLock
            }
            val currentActor =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (currentActor is Result.Failed) return@run currentActor
            val live = (currentActor as Result.Success).value
            val liveAccess = live.requirePermission("approvals.read")
            if (liveAccess is Result.Failed) return@run liveAccess
            if (delegation.fromAccount != live.accountId && "approvals.manage" !in live.permissions)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val existing = approvals.findDelegation(company, delegation.id)
            if (existing is Result.Failed) return@run existing
            val previous = (existing as Result.Success).value
            if (previous != null && previous.fromAccount != delegation.fromAccount)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "delegator_immutable"))
            if (previous?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val now = clock.instant()
            if (delegation.active && !delegation.validUntil.isAfter(now))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_delegation"))
            if (delegation.validUntil.isAfter(now)) {
                for (account in setOf(delegation.fromAccount, delegation.toAccount)) {
                    val counted =
                        previous != null &&
                            previous.validUntil.isAfter(now) &&
                            account in setOf(previous.fromAccount, previous.toAccount)
                    if (!counted) {
                        val total =
                            approvals.countDelegations(company, account, now, false, delegation.id)
                        if (total is Result.Failed) return@run total
                        if ((total as Result.Success).value >= 1000)
                            return@run Result.Failed(
                                Failure(FailureKind.CONFLICT, "approval_delegation_limit")
                            )
                    }
                    if (delegation.active && !(counted && previous.active)) {
                        val active =
                            approvals.countDelegations(company, account, now, true, delegation.id)
                        if (active is Result.Failed) return@run active
                        if ((active as Result.Success).value >= 200)
                            return@run Result.Failed(
                                Failure(FailureKind.CONFLICT, "approval_delegation_capacity")
                            )
                    }
                }
            }
            val candidates =
                members.candidates(
                    company,
                    setOf(delegation.fromAccount, delegation.toAccount),
                    emptySet(),
                    3,
                )
            if (candidates is Result.Failed) return@run candidates
            if (
                delegation.active &&
                    (candidates as Result.Success).value.count {
                        it.accountActive &&
                            it.membershipActive &&
                            it.permissions.any { p -> p in approvalPermissions(delegation.kind) }
                    } != 2
            )
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "approver_unavailable"))
            approvals.saveDelegation(company, delegation, expectedVersion).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "approval_delegation",
                                receipt.id,
                                "approvals.delegation_saved",
                                mapOf(
                                    "from" to delegation.fromAccount.toString(),
                                    "to" to delegation.toAccount.toString(),
                                    "active" to delegation.active.toString(),
                                ),
                                reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}

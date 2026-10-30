package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.LeaveType
import dev.fajar.hris.leave.domain.policies.validateLeaveType
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock

class SaveLeaveType(
    private val policies: LeavePolicyRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: java.util.UUID,
        type: LeaveType,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("leave.manage")
        if (access is Result.Failed) return access
        val command =
            type.copy(
                policy = type.policy.copy(allowedContracts = type.policy.allowedContracts.toSet())
            )
        val valid = validateLeaveType(command, reason)
        if (valid is Result.Failed) return valid
        if ((expectedVersion ?: 0) < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "leave.type_save",
                operationId,
                listOf(
                    command.id.toString(),
                    command.code,
                    command.effectiveFrom.toString(),
                    command.policy.name,
                    command.policy.paid.toString(),
                    command.policy.allowPartialDays.toString(),
                    command.policy.minServiceMonths.toString(),
                    command.policy.allowedContracts.map { it.name }.sorted().joinToString(","),
                    command.policy.maxRequestDays.toString(),
                    command.active.toString(),
                    expectedVersion?.toString(),
                    reason,
                ) +
                    (if (command.policy.attachmentRequired) listOf("attachmentRequired", "true")
                    else emptyList()) +
                    (command.policy.accrual?.let {
                        listOf(
                            "accrual",
                            it.frequency.name,
                            it.halfDaysPerPeriod.toString(),
                            it.carryLimitHalfDays.toString(),
                        )
                    } ?: emptyList()) +
                    (command.policy.accrual?.carryValidityMonths?.let {
                        listOf("carryValidityMonths", it.toString())
                    } ?: emptyList()),
            )
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = policies.lock(company)
            if (lock is Result.Failed) return@run lock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val sessionAccess = (authority as Result.Success).value
            val checked =
                validateCompanySessionActor(actor, sessionAccess, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val allowed = (checked as Result.Success).value.requirePermission("leave.manage")
            if (allowed is Result.Failed) return@run allowed
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val existing = policies.find(company, command.id)
            if (existing is Result.Failed) return@run existing
            val current = (existing as Result.Success).value
            if (current?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (current != null && current.code != command.code)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "leave_type_code_immutable")
                )
            policies.save(actor, command, expectedVersion, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "leave_type",
                                command.id,
                                "leave.policy_saved",
                                reason = reason,
                            ),
                        )
                    }
                    .flatMap {
                        validateCompanySessionActor(actor, sessionAccess, clock.instant(), security)
                            .map { receipt }
                    }
            }
        }
    }
}

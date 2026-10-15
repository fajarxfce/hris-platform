package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class CancelPayrollAmendment(
    private val amendments: PayrollAmendmentRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
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
        id: UUID,
        expectedVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = requirePayrollMutation(actor, "payroll.amend", clock.instant(), security)
        if (permission is Result.Failed) return permission
        val valid = validatePayrollAmendmentCommand(expectedVersion, reason)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "payroll.amendment_cancel",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val payrollGuard = policies.lock(company)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val allowed =
                requirePayrollMutation(
                    (checked as Result.Success).value,
                    "payroll.amend",
                    clock.instant(),
                    security,
                )
            if (allowed is Result.Failed) return@run allowed
            val found = amendments.find(company, id)
            if (found is Result.Failed) return@run found
            val current =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_not_found")
                    )
            val beneficiary = people.accountForEmployee(company, current.summary.employee.id)
            if (beneficiary is Result.Failed) return@run beneficiary
            if ((beneficiary as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_amendment_self_preparation_forbidden")
                )
            (replay as Result.Success).value?.let { receipt ->
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .flatMap {
                        requirePayrollMutation(it, "payroll.amend", clock.instant(), security).map {
                            receipt
                        }
                    }
            }
            if (current.summary.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                current.summary.status !in
                    setOf(
                        PayrollAmendmentStatus.DRAFT,
                        PayrollAmendmentStatus.CALCULATED,
                        PayrollAmendmentStatus.REJECTED,
                    )
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_not_editable")
                )
            if (current.summary.version == PAYROLL_AMENDMENT_MAXIMUM_VERSION)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_revision_limit")
                )
            val cancelled =
                current.copy(
                    summary =
                        current.summary.copy(
                            version = expectedVersion + 1,
                            status = PayrollAmendmentStatus.CANCELLED,
                            action = PayrollAmendmentAction.CANCELLED,
                            actorId = actor.accountId,
                            recordedAt = clock.instant(),
                            reason = reason,
                        )
                )
            amendments.save(company, cancelled, expectedVersion).flatMap { receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "payroll_amendment",
                            id,
                            "payroll.amendment_cancelled",
                            mapOf("version" to receipt.version.toString()),
                            reason,
                        ),
                    )
                    .flatMap { operations.record(actor, key, receipt) }
                    .flatMap {
                        validateCompanySessionActor(actor, access, clock.instant(), security)
                    }
                    .flatMap {
                        requirePayrollMutation(it, "payroll.amend", clock.instant(), security).map {
                            receipt
                        }
                    }
            }
        }
    }
}

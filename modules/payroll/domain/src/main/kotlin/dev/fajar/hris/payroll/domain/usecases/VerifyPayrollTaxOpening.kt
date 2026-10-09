package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class VerifyPayrollTaxOpening(
    private val openings: PayrollTaxOpeningRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employee: UUID,
        year: Int,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = actor.requirePermission("payroll.review")
        if (permission is Result.Failed) return permission
        if (year !in 2024..2100 || version !in 0..999 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_tax_opening"))
        val key =
            OperationKey(
                "payroll.tax_opening_verify",
                operationId,
                listOf(employee.toString(), year.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val policyLock = policies.lock(company)
            if (policyLock is Result.Failed) return@run policyLock
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val allowed =
                requirePayrollMutation(
                    (checked as Result.Success).value,
                    "payroll.review",
                    clock.instant(),
                    security,
                )
            if (allowed is Result.Failed) return@run allowed
            val beneficiary = people.accountForEmployee(company, employee)
            if (beneficiary is Result.Failed) return@run beneficiary
            if ((beneficiary as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "self_payroll_input_review_denied")
                )
            val found = openings.find(company, employee, year)
            if (found is Result.Failed) return@run found
            val current =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_tax_opening_not_found")
                    )
            val authors = openings.authors(company, current.id)
            if (authors is Result.Failed) return@run authors
            if (actor.accountId in (authors as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_tax_opening_author_cannot_verify")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (current.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (current.status != PayrollTaxOpeningStatus.DRAFT)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_tax_opening_already_verified")
                )
            if (version == 999L)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_tax_opening_revision_limit")
                )
            val verified =
                current.copy(
                    version = version + 1,
                    status = PayrollTaxOpeningStatus.VERIFIED,
                    verifiedBy = actor.accountId,
                    recordedAt = clock.instant(),
                    reason = reason,
                )
            openings.save(company, verified, version).flatMap { receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "payroll_tax_opening",
                            current.id,
                            "payroll.tax_opening_verified",
                            mapOf("employeeId" to employee.toString(), "year" to year.toString()),
                            reason,
                        ),
                    )
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}

package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollPayslipReadScope
import dev.fajar.hris.payroll.domain.entities.PayrollPayslipSummary
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.PayrollAssessmentRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class ListPayrollPayslips(
    private val assessments: PayrollAssessmentRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        from: YearMonth?,
        until: YearMonth?,
        employeeId: UUID?,
        after: String?,
        limit: Int,
        scope: PayrollPayslipReadScope = PayrollPayslipReadScope.AUTHORIZED,
        query: String = "",
    ): Result<Page<PayrollPayslipSummary>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollPayslips(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        if (query.length > 120 || query.any { it.isISOControl() })
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_payroll_payslip_search",
                    fields = mapOf("query" to "invalid"),
                    parameters = mapOf("maximumLength" to "120"),
                )
            )
        val decoded = decodePayrollPayslipCursor(after)
        if (decoded is Result.Failed) return decoded
        val cursor = (decoded as Result.Success).value
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val accessed = identities.access(actor.accountId, company)
            if (accessed is Result.Failed) return@run accessed
            val access = (accessed as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val current = (checked as Result.Success).value
            if (!canReadPayrollPayslips(current))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val companyDetails =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "company_access_denied")
                    )
            val end = until ?: YearMonth.now(clock.withZone(ZoneId.of(companyDetails.timezone)))
            val start = from ?: end.minusMonths(11)
            val range = validatePayrollPayslipRange(start, end, cursor)
            if (range is Result.Failed) return@run range
            val allowedEmployees =
                if (
                    scope == PayrollPayslipReadScope.AUTHORIZED &&
                        "payroll.read" in current.permissions
                )
                    null
                else {
                    val owned = people.employeeIdsForAccount(company, actor.accountId, 201)
                    if (owned is Result.Failed) return@run owned
                    val ids = (owned as Result.Success).value
                    if (ids.size > 200)
                        return@run Result.Failed(
                            Failure(
                                FailureKind.VALIDATION,
                                "payroll_scope_limit",
                                parameters = mapOf("maximumEmployments" to "200"),
                            )
                        )
                    if (employeeId != null && employeeId !in ids)
                        return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
                    ids.toSet()
                }
            assessments
                .payslips(
                    company,
                    start,
                    end,
                    employeeId,
                    allowedEmployees,
                    cursor,
                    limit,
                    query.trim(),
                )
                .flatMap { page ->
                    validateCompanySessionActor(actor, access, clock.instant(), security).map {
                        page
                    }
                }
        }
    }
}

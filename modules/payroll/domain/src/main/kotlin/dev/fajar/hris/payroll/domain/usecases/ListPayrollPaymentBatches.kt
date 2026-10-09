package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.PayrollPaymentRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class ListPayrollPaymentBatches(
    private val payments: PayrollPaymentRepository,
    private val companies: CompanyRepository,
    private val people: PeopleRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        from: LocalDate,
        until: LocalDate,
        status: PayrollPaymentBatchStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollPaymentSummary>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (
            limit !in 1..200 ||
                from.year !in 1900..2200 ||
                until.year !in 1900..2200 ||
                java.time.temporal.ChronoUnit.DAYS.between(from, until) !in 0..365
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val access = validatePayrollPaymentAccess(actor, clock.instant(), security)
        if (access is Result.Failed) return access
        return transactions.run(actor) {
            val resource = payments.lock(company, shared = true)
            if (resource is Result.Failed) return@run resource
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val recent =
                validatePayrollPaymentAccess(
                    (checked as Result.Success).value,
                    clock.instant(),
                    security,
                )
            if (recent is Result.Failed) return@run recent
            val found = companies.find(company)
            if (found is Result.Failed) return@run found
            val settings =
                (found as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val zone = ZoneId.of(settings.timezone)
            payments.list(
                company,
                from.atStartOfDay(zone).toInstant(),
                until.plusDays(1).atStartOfDay(zone).toInstant(),
                status,
                after,
                limit,
            )
        }
    }
}

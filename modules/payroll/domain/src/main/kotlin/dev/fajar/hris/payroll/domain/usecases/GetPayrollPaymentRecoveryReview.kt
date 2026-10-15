package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.PayrollPaymentRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class GetPayrollPaymentRecoveryReview(
    private val payments: PayrollPaymentRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, assessmentId: UUID): Result<PayrollPaymentRecoveryReview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = validatePayrollPaymentAccess(actor, clock.instant(), security)
        if (permission is Result.Failed) return permission
        return transactions.run(actor) {
            val paymentGuard = payments.lock(company, shared = true)
            if (paymentGuard is Result.Failed) return@run paymentGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val permitted = validatePayrollPaymentActor(actor, access, clock.instant(), security)
            if (permitted is Result.Failed) return@run permitted
            val candidates = payments.candidates(company, setOf(assessmentId))
            if (candidates is Result.Failed) return@run candidates
            val assessment =
                (candidates as Result.Success).value.singleOrNull()
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_payslip_not_found")
                    )
            val owner = people.accountForEmployee(company, assessment.employmentId)
            if (owner is Result.Failed) return@run owner
            val found = payments.progress(company, assessmentId, null)
            if (found is Result.Failed) return@run found
            val progress =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_payslip_not_found")
                    )
            val recoveries = payments.recoveries(company, assessmentId)
            if (recoveries is Result.Failed) return@run recoveries
            validatePayrollPaymentActor(actor, access, clock.instant(), security).map {
                payrollPaymentRecoveryReview(
                    (owner as Result.Success).value == actor.accountId,
                    assessment,
                    progress,
                    (recoveries as Result.Success).value,
                )
            }
        }
    }
}

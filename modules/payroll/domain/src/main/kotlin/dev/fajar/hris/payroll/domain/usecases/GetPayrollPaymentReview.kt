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

class GetPayrollPaymentReview(
    private val payments: PayrollPaymentRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        historyAfter: Long?,
        historyLimit: Int,
    ): Result<PayrollPaymentReview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (historyLimit !in 1..200 || (historyAfter != null && historyAfter < 0))
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
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val liveAccess = (acquired as Result.Success).value
            val checked = validatePayrollPaymentActor(actor, liveAccess, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val found = payments.find(company, id)
            if (found is Result.Failed) return@run found
            val batch =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_payment_batch_not_found")
                    )
            if (historyAfter != null && historyAfter > batch.version)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            val history = payments.history(company, id, historyAfter, historyLimit)
            if (history is Result.Failed) return@run history
            val results = payments.results(company, id)
            if (results is Result.Failed) return@run results
            val candidates =
                if (
                    batch.status in
                        setOf(
                            PayrollPaymentBatchStatus.PREPARED,
                            PayrollPaymentBatchStatus.RELEASED,
                        )
                )
                    payments.candidates(company, batch.items.map { it.assessmentId }.toSet())
                else Result.Success(emptyList())
            if (candidates is Result.Failed) return@run candidates
            validatePayrollPaymentActor(actor, liveAccess, clock.instant(), security).map { live ->
                PayrollPaymentReview(
                    batch,
                    (history as Result.Success).value,
                    (results as Result.Success).value,
                    payrollPaymentAvailableActions(
                        live.accountId,
                        batch,
                        (candidates as Result.Success).value,
                    ),
                )
            }
        }
    }
}

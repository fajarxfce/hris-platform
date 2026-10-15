package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.PayrollPaymentRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.math.BigDecimal
import java.time.*
import java.util.UUID

class ConfirmPayrollPaymentRecovery(
    private val payments: PayrollPaymentRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        assessmentId: UUID,
        id: UUID,
        assessmentRevision: Long,
        paymentVersion: Long,
        amount: BigDecimal,
        transactionReference: String,
        occurredAt: Instant,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = validatePayrollPaymentAccess(actor, clock.instant(), security)
        if (permission is Result.Failed) return permission
        if (
            assessmentRevision < 0 ||
                paymentVersion < 0 ||
                amount.signum() <= 0 ||
                amount.scale() > 2 ||
                amount > BigDecimal("999999999999.99") ||
                reason.isBlank() ||
                reason.length > 1000 ||
                !transactionReference.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}")) ||
                occurredAt.isAfter(clock.instant())
        )
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_payroll_payment_recovery")
            )
        val key =
            OperationKey(
                "payroll.payment_recovery",
                operationId,
                listOf(
                    assessmentId.toString(),
                    id.toString(),
                    assessmentRevision.toString(),
                    paymentVersion.toString(),
                    amount.toPlainString(),
                    transactionReference,
                    occurredAt.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val paymentGuard = payments.lock(company)
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
            val candidate =
                (candidates as Result.Success).value.singleOrNull()
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_payslip_not_found")
                    )
            val owner = people.accountForEmployee(company, candidate.employmentId)
            if (owner is Result.Failed) return@run owner
            if ((owner as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_self_recovery_forbidden")
                )
            (replay as Result.Success).value?.let { receipt ->
                return@run validatePayrollPaymentActor(actor, access, clock.instant(), security)
                    .map { receipt }
            }
            if (
                candidate.assessmentRevision != assessmentRevision ||
                    candidate.paymentVersion != paymentVersion
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_payment_balance_changed")
                )
            if (
                candidate.amount.signum() >= 0 ||
                    amount > candidate.amount.negate() ||
                    occurredAt.isBefore(candidate.publishedAt)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_payment_recovery_exceeds_balance")
                )
            val found = payments.recoveries(company, assessmentId)
            if (found is Result.Failed) return@run found
            val recoveries = (found as Result.Success).value
            if (recoveries.size >= 20)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_payment_recovery_limit")
                )
            if (recoveries.any { it.id == id })
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_payment_recovery_exists")
                )
            val progress = payments.progress(company, assessmentId, null)
            if (progress is Result.Failed) return@run progress
            val current =
                (progress as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_payment_balance_changed")
                    )
            if (
                current.attempts.any {
                    it.status in
                        setOf(PayrollPaymentItemStatus.PREPARED, PayrollPaymentItemStatus.PENDING)
                }
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_payment_pending")
                )
            if (
                current.attempts.any {
                    it.status == PayrollPaymentItemStatus.SUCCEEDED &&
                        it.resolvedAt?.isAfter(occurredAt) == true
                }
            )
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_payroll_payment_recovery")
                )
            val recovery =
                PayrollPaymentRecovery(
                    id,
                    assessmentId,
                    assessmentRevision,
                    paymentVersion,
                    amount,
                    transactionReference,
                    occurredAt,
                    clock.instant(),
                    actor.accountId,
                    reason,
                )
            val receipt = MutationReceipt(id, 0)
            payments
                .recordRecovery(company, recovery)
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "payroll_payment_recovery",
                            id,
                            "payroll.payment_recovered",
                            mapOf(
                                "assessmentId" to assessmentId.toString(),
                                "assessmentRevision" to assessmentRevision.toString(),
                            ),
                            reason,
                        ),
                    )
                }
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap { validatePayrollPaymentActor(actor, access, clock.instant(), security) }
                .map { receipt }
        }
    }
}

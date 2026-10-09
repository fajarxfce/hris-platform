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

class ReconcilePayrollPaymentBatch(
    private val payments: PayrollPaymentRepository,
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
        id: UUID,
        version: Long,
        results: List<PayrollPaymentResult>,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validatePayrollPaymentAccess(actor, clock.instant(), security)
        if (access is Result.Failed) return access
        if (version < 0 || reason.isBlank() || reason.length > 1000 || results.size !in 1..100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_payment_action"))
        val ordered = results.sortedBy { it.itemId }
        val key =
            OperationKey(
                "payroll.payment_reconcile",
                operationId,
                listOf(id.toString(), version.toString(), reason) +
                    ordered.flatMap {
                        listOf(
                            it.itemId.toString(),
                            it.status.name,
                            it.transactionReference,
                            it.occurredAt.toString(),
                            it.reason,
                            it.confirmedNoTransfer.toString(),
                        )
                    },
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val resource = payments.lock(company)
            if (resource is Result.Failed) return@run resource
            val structure = people.lockReportingLines(company, shared = true)
            if (structure is Result.Failed) return@run structure
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
            val live = (checked as Result.Success).value
            val recent = validatePayrollPaymentAccess(live, clock.instant(), security)
            if (recent is Result.Failed) return@run recent
            val found = payments.find(company, id)
            if (found is Result.Failed) return@run found
            val batch =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_payment_batch_not_found")
                    )
            val candidates =
                payments.candidates(company, batch.items.map { it.assessmentId }.toSet())
            if (candidates is Result.Failed) return@run candidates
            val evidence = (candidates as Result.Success).value
            if (evidence.size != batch.itemCount)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_payment_evidence_unavailable")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (batch.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val now = clock.instant()
            val valid = validatePayrollPaymentResults(batch, ordered, now)
            if (valid is Result.Failed) return@run valid
            val allResolved =
                batch.items.count { it.status == PayrollPaymentItemStatus.PENDING } == ordered.size
            val reconciliation =
                PayrollPaymentReconciliation(
                    id,
                    version + 1,
                    actor.accountId,
                    now,
                    if (allResolved) PayrollPaymentBatchStatus.CLOSED
                    else PayrollPaymentBatchStatus.RELEASED,
                    ordered,
                )
            payments.reconcile(actor, batch, reconciliation, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "payroll_payment_batch",
                                id,
                                "payroll.payment_reconciled",
                                mapOf(
                                    "resultCount" to results.size.toString(),
                                    "status" to reconciliation.resultingStatus.name,
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

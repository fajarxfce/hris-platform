package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePaymentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class ReconcileExpensePaymentBatch(
    private val payments: ExpensePaymentRepository,
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
        results: List<ExpensePaymentResult>,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validateExpensePaymentAccess(actor, clock.instant(), security)
        if (access is Result.Failed) return access
        if (version < 0 || reason.isBlank() || reason.length > 1000 || results.size !in 1..100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_expense_payment_action"))
        val ordered = results.sortedBy { it.itemId }
        val key =
            OperationKey(
                "expenses.payment_reconcile",
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
            val structure = people.lockReportingLines(company)
            if (structure is Result.Failed) return@run structure
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val recent = validateExpensePaymentAccess(live, clock.instant(), security)
            if (recent is Result.Failed) return@run recent
            val found = payments.find(company, id)
            if (found is Result.Failed) return@run found
            val batch =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_payment_batch_not_found")
                    )
            val candidates =
                payments.candidates(company, batch.items.map { it.submissionId }.toSet())
            if (candidates is Result.Failed) return@run candidates
            val evidence = (candidates as Result.Success).value
            if (evidence.size != batch.itemCount)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_payment_evidence_unavailable")
                )
            val independent = validateExpensePaymentIndependence(actor.accountId, evidence)
            if (independent is Result.Failed) return@run independent
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (batch.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val now = clock.instant()
            val valid = validateExpensePaymentResults(batch, ordered, now)
            if (valid is Result.Failed) return@run valid
            val allResolved =
                batch.items.count { it.status == ExpensePaymentItemStatus.PENDING } == ordered.size
            val reconciliation =
                ExpensePaymentReconciliation(
                    id,
                    version + 1,
                    actor.accountId,
                    now,
                    if (allResolved) ExpensePaymentBatchStatus.CLOSED
                    else ExpensePaymentBatchStatus.RELEASED,
                    ordered,
                )
            payments.reconcile(actor, batch, reconciliation, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "expense_payment_batch",
                                id,
                                "expenses.payment_reconciled",
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

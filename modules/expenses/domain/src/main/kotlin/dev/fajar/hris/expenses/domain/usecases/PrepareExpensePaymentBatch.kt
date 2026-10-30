package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePaymentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.math.BigDecimal
import java.time.*
import java.util.UUID

class PrepareExpensePaymentBatch(
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
        title: String,
        items: List<ExpensePaymentInstruction>,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validateExpensePaymentAccess(actor, clock.instant(), security)
        if (access is Result.Failed) return access
        val ordered = items.take(101).sortedBy { it.id }
        val valid = validateExpensePaymentInstructions(title, ordered, reason)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "expenses.payment_prepare",
                operationId,
                listOf(id.toString(), title, reason) +
                    ordered.flatMap {
                        listOf(
                            it.id.toString(),
                            it.submissionId.toString(),
                            it.destination.bankCode,
                            it.destination.accountNumber,
                            it.destination.accountName,
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
            val accessed = identities.access(actor.accountId, company)
            if (accessed is Result.Failed) return@run accessed
            val identity = (accessed as Result.Success).value
            val checked =
                validateExpensePaymentSessionActor(actor, identity, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val candidates = payments.candidates(company, ordered.map { it.submissionId }.toSet())
            if (candidates is Result.Failed) return@run candidates
            val evidence = (candidates as Result.Success).value
            if (evidence.size != ordered.size)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_payment_requires_approved_claims")
                )
            val independent = validateExpensePaymentIndependence(actor.accountId, evidence)
            if (independent is Result.Failed) return@run independent
            (replay as Result.Success).value?.let { receipt ->
                return@run validateExpensePaymentSessionActor(
                        actor,
                        identity,
                        clock.instant(),
                        security,
                    )
                    .map { receipt }
            }
            val found = payments.find(company, id)
            if (found is Result.Failed) return@run found
            if ((found as Result.Success).value != null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_payment_batch_exists")
                )
            val capacity = payments.capacity(company)
            if (capacity is Result.Failed) return@run capacity
            val counts = (capacity as Result.Success).value
            if (counts.totalBatches >= 10000 || counts.openBatches >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "expense_payment_capacity"))
            val claimIds = evidence.map { it.claimId }.toSet()
            val occupied = payments.occupiedClaims(company, claimIds)
            if (occupied is Result.Failed) return@run occupied
            if ((occupied as Result.Success).value.isNotEmpty())
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_claim_payment_reserved")
                )
            val attempts = payments.attempts(company, claimIds)
            if (attempts is Result.Failed) return@run attempts
            if ((attempts as Result.Success).value.values.any { it >= 20 })
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_payment_attempt_limit")
                )
            val bySubmission = evidence.associateBy { it.submissionId }
            val snapshot =
                ordered.map { instruction ->
                    val payable = bySubmission.getValue(instruction.submissionId)
                    ExpensePaymentItem(
                        instruction.id,
                        payable.claimId,
                        payable.submissionId,
                        payable.employmentId,
                        payable.employeeNumber,
                        payable.employeeName,
                        payable.amount,
                        instruction.destination,
                        ExpensePaymentItemStatus.PREPARED,
                        0,
                        0,
                        null,
                        null,
                    )
                }
            val batch =
                ExpensePaymentBatch(
                    id,
                    ExpensePaymentBatchStatus.PREPARED,
                    0,
                    title,
                    snapshot.fold(BigDecimal.ZERO) { total, item -> total + item.amount },
                    snapshot.size,
                    actor.accountId,
                    clock.instant(),
                    null,
                    null,
                    snapshot,
                )
            payments.prepare(actor, batch, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "expense_payment_batch",
                                id,
                                "expenses.payment_prepared",
                                mapOf("itemCount" to ordered.size.toString()),
                                reason,
                            ),
                        )
                    }
                    .flatMap {
                        validateExpensePaymentSessionActor(
                            actor,
                            identity,
                            clock.instant(),
                            security,
                        )
                    }
                    .map { receipt }
            }
        }
    }
}

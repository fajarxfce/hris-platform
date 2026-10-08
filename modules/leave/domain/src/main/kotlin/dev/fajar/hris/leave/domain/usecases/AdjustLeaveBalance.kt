package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.validateLeaveBalanceAdjustment
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class AdjustLeaveBalance(
    private val ledger: LeaveLedgerRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
        delta: Int,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("leave.manage")
        if (access is Result.Failed) return access
        if (
            year !in 1900..2200 ||
                delta == 0 ||
                delta !in -732..732 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_adjustment"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "leave.balance_adjust",
                operationId,
                listOf(
                    employeeId.toString(),
                    typeId.toString(),
                    year.toString(),
                    delta.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val employeeResult = people.find(company, employeeId, LocalDate.of(year, 12, 31))
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.person.accountId == actor.accountId)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "self_adjustment_denied"))
            val typeResult = policies.effective(company, typeId, LocalDate.of(year, 12, 31))
            if (typeResult is Result.Failed) return@run typeResult
            if ((typeResult as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "leave_type_unavailable"))
            val lock = ledger.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val balance = ledger.balance(company, employeeId, typeId, year)
            if (balance is Result.Failed) return@run balance
            val valid = validateLeaveBalanceAdjustment((balance as Result.Success).value, delta)
            if (valid is Result.Failed) return@run valid
            val entryId = UUID.randomUUID()
            val entry =
                LeaveLedgerEntry(
                    entryId,
                    employeeId,
                    typeId,
                    year,
                    LeaveLedgerKind.ADJUSTMENT,
                    entryId,
                    null,
                    delta,
                    0,
                    0,
                    actor.accountId,
                    clock.instant(),
                    reason,
                )
            val receipt = MutationReceipt(entry.id, 0)
            ledger
                .append(company, listOf(entry))
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "leave_ledger",
                            entry.id,
                            "leave.balance_adjusted",
                            mapOf(
                                "employmentId" to employeeId.toString(),
                                "typeId" to typeId.toString(),
                                "year" to year.toString(),
                            ),
                            reason,
                        ),
                    )
                }
                .map { receipt }
        }
    }
}

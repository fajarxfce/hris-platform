package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveLedger
import dev.fajar.hris.leave.domain.policies.canReadLeave
import dev.fajar.hris.leave.domain.repositories.LeaveLedgerRepository
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class GetLeaveLedger(
    private val ledger: LeaveLedgerRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
        after: UUID?,
        limit: Int,
    ): Result<LeaveLedger> {
        if (year !in 1900..2200 || limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val type = policies.find(company, typeId)
            if (type is Result.Failed) return@run type
            if ((type as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "leave_type_not_found"))
            people.find(company, employeeId, LocalDate.of(year, 12, 31)).flatMap { employee ->
                if (employee == null)
                    return@flatMap Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
                people.findAtInstant(company, employeeId, clock.instant()).flatMap { current ->
                    if (!canReadLeave(actor, employee, current))
                        Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
                    else
                        ledger.lock(company, employeeId).flatMap {
                            ledger.balance(company, employeeId, typeId, year).flatMap { balance ->
                                ledger
                                    .entries(company, employeeId, typeId, year, after, limit)
                                    .map { LeaveLedger(balance, it) }
                            }
                        }
                }
            }
        }
    }
}

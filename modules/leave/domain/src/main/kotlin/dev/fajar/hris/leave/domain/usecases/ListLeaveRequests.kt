package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.canReadLeave
import dev.fajar.hris.leave.domain.repositories.LeaveRequestRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class ListLeaveRequests(
    private val requests: LeaveRequestRepository,
    private val people: PeopleRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID?,
        status: LeaveStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<LeaveRequestSummary>> {
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            if ("leave.read" !in actor.permissions) {
                if (employeeId == null)
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "employee_scope_required")
                    )
                val result = people.findAtInstant(company, employeeId, clock.instant())
                if (result is Result.Failed) return@run result
                val employee = (result as Result.Success).value
                if (employee == null || !canReadLeave(actor, employee, employee))
                    return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            }
            requests.list(company, employeeId, status, after, limit)
        }
    }
}

package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.ShiftDefinition
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository

class ListShifts(
    private val schedules: ScheduleRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<ShiftDefinition>> =
        actor.requirePermission("workforce.read").flatMap {
            if (query.length > 120 || (after?.length ?: 0) > 32 || limit !in 1..200)
                Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            else
                transactions.run(actor) {
                    schedules.shifts(requireNotNull(actor.companyId), query.trim(), after, limit)
                }
        }
}

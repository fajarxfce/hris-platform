package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.ReportingAssignment

/** Check every effective boundary, including already scheduled future changes. */
fun validateReportingLine(
    change: ReportingAssignment,
    history: List<ReportingAssignment>,
): Result<Unit> {
    val assignments = history + change
    val byEmployee = assignments.groupBy { it.employeeId }
    val boundaries =
        (assignments.map { it.effectiveFrom } + change.effectiveFrom)
            .filter { !it.isBefore(change.effectiveFrom) }
            .distinct()
    for (date in boundaries) {
        var current = change.employeeId
        val visited = mutableSetOf<java.util.UUID>()
        while (true) {
            if (!visited.add(current) || visited.size > 32)
                return Result.Failed(Failure(FailureKind.CONFLICT, "reporting_cycle_or_depth"))
            val parent =
                byEmployee[current]
                    ?.asSequence()
                    ?.filter { !it.effectiveFrom.isAfter(date) }
                    ?.maxWithOrNull(
                        compareBy<ReportingAssignment> { it.effectiveFrom }.thenBy { it.revision }
                    )
                    ?.managerId ?: break
            current = parent
        }
    }
    return Result.Success(Unit)
}

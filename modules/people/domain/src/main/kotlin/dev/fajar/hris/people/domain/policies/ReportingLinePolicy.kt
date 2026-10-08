package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.ReportingAssignment

/** Check every effective boundary, including already scheduled future changes. */
fun validateReportingLine(
    change: ReportingAssignment,
    history: List<ReportingAssignment>,
): Result<Unit> =
    validateReportingHistory(change.employeeId, change.effectiveFrom, history + change)

fun validateReportingHistory(
    employeeId: java.util.UUID,
    from: java.time.LocalDate,
    history: List<ReportingAssignment>,
): Result<Unit> {
    val byEmployee = history.groupBy { it.employeeId }
    val boundaries =
        (history.map { it.effectiveFrom } + from).filter { !it.isBefore(from) }.distinct()
    for (date in boundaries) {
        var current = employeeId
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

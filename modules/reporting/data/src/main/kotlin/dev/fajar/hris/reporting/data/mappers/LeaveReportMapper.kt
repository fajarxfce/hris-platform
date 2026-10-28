package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.leave.domain.entities.LeaveStatus
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.domain.entities.*

fun List<LeaveReportAggregateRow>.toLeaveReportAggregate(): LeaveReportAggregate {
    check(size in 1..6)
    val indexed = associateBy { it.status?.let(LeaveStatus::valueOf) }
    check(indexed.size == size)
    val total = requireNotNull(indexed[null]).toCounts()
    val statuses =
        LeaveStatus.entries.map {
            LeaveStatusCounts(it, indexed[it]?.toCounts() ?: LeaveReportCounts())
        }
    fun sum(value: (LeaveReportCounts) -> Long) =
        statuses.fold(0L) { count, row -> Math.addExact(count, value(row.counts)) }
    check(sum { it.requests } == total.requests && sum { it.halfDays } == total.halfDays)
    check(total.employments in statuses.maxOf { it.counts.employments }..sum { it.employments })
    return LeaveReportAggregate(total, statuses)
}

private fun LeaveReportAggregateRow.toCounts(): LeaveReportCounts {
    check(invalidRequests == 0L && requests >= 0 && employments in 0..requests)
    check((requests == 0L) == (employments == 0L))
    check(halfDays in requests..Math.multiplyExact(requests, 732))
    return LeaveReportCounts(requests, employments, halfDays)
}

fun LeaveReportRequestRow.toLeaveReportRequest(): LeaveReportRequest {
    check(!invalid && version >= 0 && requestHalfDays in 1..732 && halfDays in 1..requestHalfDays)
    check(employeeNumber.isNotBlank() && employeeNumber.length <= 32)
    check(employeeName.isNotBlank() && employeeName.length <= 200)
    check(
        typeCode.isNotBlank() &&
            typeCode.length <= 32 &&
            typeName.isNotBlank() &&
            typeName.length <= 200
    )
    check(submittedAt.year in 1..9999)
    return LeaveReportRequest(
        id,
        employeeId,
        employeeNumber,
        employeeName,
        typeCode,
        typeName,
        LeaveStatus.valueOf(status),
        submittedAt.toInstant(),
        version,
        halfDays,
        requestHalfDays,
    )
}

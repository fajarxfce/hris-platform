package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.reporting.data.dto.OvertimeReportAggregateRow
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

fun List<OvertimeReportAggregateRow>.toOvertimeReportAggregate(
    from: LocalDate,
    until: LocalDate,
    status: OvertimeStatus?,
): OvertimeReportAggregate {
    check(ChronoUnit.DAYS.between(from, until) in 0..365)
    val first = YearMonth.from(from)
    val last = YearMonth.from(until)
    val length = ChronoUnit.MONTHS.between(first, last)
    check(length in 0..12 && size in 1..14)
    val indexed = associateBy { it.month }
    check(
        indexed.size == size &&
            indexed.keys.all {
                it == null || (it.dayOfMonth == 1 && YearMonth.from(it) in first..last)
            }
    )
    val totals = requireNotNull(indexed[null]).toCounts(status)
    val months =
        (0L..length).map { offset ->
            val month = first.plusMonths(offset)
            OvertimeReportMonth(
                month,
                maxOf(from, month.atDay(1)),
                minOf(until, month.atEndOfMonth()),
                indexed[month.atDay(1)]?.toCounts(status) ?: OvertimeReportCounts(),
            )
        }
    fun sum(value: (OvertimeReportCounts) -> Long) =
        months.fold(0L) { count, row -> Math.addExact(count, value(row.counts)) }
    check(
        sum { it.requests } == totals.requests && sum { it.actualRequests } == totals.actualRequests
    )
    check(
        sum { it.requestedMinutes } == totals.requestedMinutes &&
            sum { it.actualMinutes } == totals.actualMinutes &&
            sum { it.approvedMinutes } == totals.approvedMinutes
    )
    check(totals.employments in months.maxOf { it.counts.employments }..sum { it.employments })
    return OvertimeReportAggregate(totals, months)
}

private fun OvertimeReportAggregateRow.toCounts(status: OvertimeStatus?): OvertimeReportCounts {
    check(requests >= 0 && employments in 0..requests && (requests == 0L) == (employments == 0L))
    check(
        actualRequests in 0..requests &&
            requestedMinutes in requests..Math.multiplyExact(requests, 720)
    )
    check(
        actualMinutes in actualRequests..Math.multiplyExact(actualRequests, 720) &&
            actualMinutes <= requestedMinutes
    )
    check(approvedMinutes in 0..actualMinutes)
    when (status) {
        OvertimeStatus.PLANNED -> check(actualRequests == 0L)
        OvertimeStatus.PENDING,
        OvertimeStatus.APPROVED,
        OvertimeStatus.REJECTED -> check(actualRequests == requests)
        else -> Unit
    }
    if (status == OvertimeStatus.APPROVED) check(approvedMinutes == actualMinutes)
    else if (status != null) check(approvedMinutes == 0L)
    return OvertimeReportCounts(
        requests,
        employments,
        actualRequests,
        requestedMinutes,
        actualMinutes,
        approvedMinutes,
    )
}

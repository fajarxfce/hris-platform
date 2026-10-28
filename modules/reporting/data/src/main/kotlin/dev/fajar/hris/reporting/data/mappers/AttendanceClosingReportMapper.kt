package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.domain.entities.*
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun List<AttendanceClosingReportRow>.toAttendanceClosingReportAggregate(
    from: YearMonth,
    until: YearMonth,
): AttendanceClosingReportAggregate {
    val length = ChronoUnit.MONTHS.between(from, until)
    check(from.year in 2000..2100 && until.year in 2000..2100 && length in 0..11 && size in 1..13)
    val indexed = associateBy { it.month }
    check(
        indexed.size == size &&
            indexed.keys.all {
                it == null || (it.dayOfMonth == 1 && YearMonth.from(it) in from..until)
            }
    )
    val totalRow = requireNotNull(indexed[null])
    check(
        totalRow.periodId == null &&
            totalRow.jobId == null &&
            totalRow.version == null &&
            totalRow.closedAt == null &&
            totalRow.timezone == null
    )
    val totals = totalRow.toCounts()
    val months =
        (0L..length).map { offset ->
            val month = from.plusMonths(offset)
            val row = indexed[month.atDay(1)]
            if (row == null) AttendanceClosingReportMonth(month, null, AttendanceClosingCounts())
            else {
                val counts = row.toCounts()
                check(
                    counts.closedMonths == 1L &&
                        counts.employmentMonths == counts.employments &&
                        counts.days == counts.employments * month.lengthOfMonth()
                )
                check(
                    requireNotNull(row.version) > 0 &&
                        requireNotNull(row.timezone) in ZoneId.getAvailableZoneIds()
                )
                AttendanceClosingReportMonth(
                    month,
                    AttendanceClosingReference(
                        requireNotNull(row.periodId),
                        requireNotNull(row.jobId),
                        row.version,
                        requireNotNull(row.closedAt).toInstant(),
                        row.timezone,
                    ),
                    counts,
                )
            }
        }
    val closings = months.mapNotNull { it.closing }
    check(
        closings.map { it.periodId }.distinct().size == closings.size &&
            closings.map { it.jobId }.distinct().size == closings.size
    )
    for (field in
        listOf<(AttendanceClosingCounts) -> Long>(
            { it.closedMonths },
            { it.employmentMonths },
            { it.adjustedEmploymentMonths },
            { it.days },
            { it.scheduledDays },
            { it.scheduledMinutes },
        )) check(months.sumOf { field(it.counts) } == field(totals))
    check(
        totals.employments in
            months.maxOf { it.counts.employments }..months.sumOf { it.counts.employments }
    )
    for (field in
        listOf<(AttendanceClosingFacts) -> Long>(
            { it.workedDays },
            { it.absenceDays },
            { it.unrecordedDays },
            { it.offDays },
            { it.unassignedDays },
            { it.acceptedMinutes },
        )) {
        check(months.sumOf { field(it.counts.original) } == field(totals.original))
        check(months.sumOf { field(it.counts.current) } == field(totals.current))
    }
    return AttendanceClosingReportAggregate(totals, java.util.List.copyOf(months))
}

private fun AttendanceClosingReportRow.toCounts(): AttendanceClosingCounts {
    val v = counts
    check(
        invalidSources == 0L &&
            v.closedMonths in 0..12 &&
            v.employmentMonths in 0..v.closedMonths * 5000
    )
    check(
        v.employments in 0..v.employmentMonths &&
            (v.employments == 0L) == (v.employmentMonths == 0L)
    )
    check(
        v.adjustedEmploymentMonths in 0..v.employmentMonths &&
            v.days in v.employmentMonths * 28..v.employmentMonths * 31
    )
    check(
        v.scheduledDays in 0..v.days &&
            v.scheduledMinutes in v.scheduledDays..v.scheduledDays * 3600
    )
    val original = v.original.toFacts(v.days)
    val current = v.current.toFacts(v.days)
    check(
        current.offDays <= original.offDays &&
            current.unrecordedDays <= original.unrecordedDays &&
            current.unassignedDays <= original.unassignedDays
    )
    if (v.adjustedEmploymentMonths == 0L) check(original == current)
    return AttendanceClosingCounts(
        v.closedMonths,
        v.employmentMonths,
        v.employments,
        v.adjustedEmploymentMonths,
        v.days,
        v.scheduledDays,
        v.scheduledMinutes,
        original,
        current,
    )
}

private fun AttendanceClosingFactsRow.toFacts(days: Long): AttendanceClosingFacts {
    val buckets = listOf(workedDays, absenceDays, unrecordedDays, offDays, unassignedDays)
    check(
        buckets.all { it in 0..days } &&
            buckets.sum() == days &&
            acceptedMinutes in 0..workedDays * 1440
    )
    return AttendanceClosingFacts(
        workedDays,
        absenceDays,
        unrecordedDays,
        offDays,
        unassignedDays,
        acceptedMinutes,
    )
}

package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.reporting.data.dto.AttendanceExceptionAggregateRow
import dev.fajar.hris.reporting.domain.entities.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun List<AttendanceExceptionAggregateRow>.toAttendanceExceptionAggregate(
    from: LocalDate,
    until: LocalDate,
): AttendanceExceptionAggregate {
    val length = ChronoUnit.DAYS.between(from, until)
    check(length in 0..30 && size in 1..32)
    val indexed = associateBy { it.date }
    check(indexed.size == size)
    check(indexed.keys.all { it == null || it in from..until })
    val total = requireNotNull(indexed[null]).toCounts()
    val days =
        (0L..length).map { offset ->
            val date = from.plusDays(offset)
            val counts = indexed[date]?.toCounts() ?: AttendanceExceptionCounts()
            check(counts.affectedEmployments == counts.affectedDays)
            AttendanceExceptionDay(date, counts)
        }
    fun sum(value: (AttendanceExceptionCounts) -> Long) =
        days.fold(0L) { count, day -> Math.addExact(count, value(day.counts)) }
    check(sum { it.observedDays } == total.observedDays)
    check(sum { it.affectedDays } == total.affectedDays)
    check(sum { it.pendingDays } == total.pendingDays)
    check(sum { it.incompleteDays } == total.incompleteDays)
    check(sum { it.pendingEvents } == total.pendingEvents)
    check(total.affectedEmployments >= days.maxOf { it.counts.affectedEmployments })
    return AttendanceExceptionAggregate(total, days)
}

private fun AttendanceExceptionAggregateRow.toCounts(): AttendanceExceptionCounts {
    check(
        observedDays >= 0 &&
            affectedDays in 0..observedDays &&
            affectedEmployments in 0..affectedDays
    )
    check((affectedDays == 0L) == (affectedEmployments == 0L))
    check(pendingDays in 0..affectedDays && incompleteDays in 0..affectedDays)
    check(affectedDays <= Math.addExact(pendingDays, incompleteDays))
    check(pendingEvents >= pendingDays && (pendingDays == 0L) == (pendingEvents == 0L))
    return AttendanceExceptionCounts(
        observedDays,
        affectedDays,
        affectedEmployments,
        pendingDays,
        incompleteDays,
        pendingEvents,
    )
}

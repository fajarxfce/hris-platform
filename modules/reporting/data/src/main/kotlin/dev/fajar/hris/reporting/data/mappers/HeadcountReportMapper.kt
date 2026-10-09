package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.reporting.data.dto.HeadcountAggregateRow
import dev.fajar.hris.reporting.domain.entities.HeadcountCounts

/** Reject malformed aggregates inside the data boundary instead of publishing misleading totals. */
fun List<HeadcountAggregateRow>.toHeadcountCounts(): HeadcountCounts {
    check(size in 1..6)
    val rows = associateBy { it.dimension to it.key }
    check(rows.size == size)
    check(all { it.employments >= 0 && it.persons in 0..it.employments })
    check(all { (it.employments == 0L) == (it.persons == 0L) })
    val total = requireNotNull(rows["TOTAL" to null])
    val allowed =
        setOf(
            "TOTAL" to null,
            "STATUS" to "ACTIVE",
            "STATUS" to "PROBATION",
            "STATUS" to "SUSPENDED",
            "CONTRACT" to "PERMANENT",
            "CONTRACT" to "FIXED_TERM",
        )
    check(rows.keys.all { it in allowed })
    val active = rows["STATUS" to "ACTIVE"]?.employments ?: 0
    val probation = rows["STATUS" to "PROBATION"]?.employments ?: 0
    val suspended = rows["STATUS" to "SUSPENDED"]?.employments ?: 0
    val permanent = rows["CONTRACT" to "PERMANENT"]?.employments ?: 0
    val fixedTerm = rows["CONTRACT" to "FIXED_TERM"]?.employments ?: 0
    check(Math.addExact(Math.addExact(active, probation), suspended) == total.employments)
    check(Math.addExact(permanent, fixedTerm) == total.employments)
    return HeadcountCounts(
        total.employments,
        total.persons,
        active,
        probation,
        suspended,
        permanent,
        fixedTerm,
    )
}

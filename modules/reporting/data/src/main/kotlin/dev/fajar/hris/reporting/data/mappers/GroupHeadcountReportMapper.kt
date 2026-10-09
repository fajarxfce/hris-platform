package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.reporting.data.dto.HeadcountAggregateRow
import dev.fajar.hris.reporting.domain.entities.CompanyHeadcount
import dev.fajar.hris.reporting.domain.entities.GroupHeadcountCounts
import dev.fajar.hris.reporting.domain.entities.HeadcountCounts
import java.util.UUID

/** Reconcile company buckets without adding distinct-person totals across companies. */
fun List<HeadcountAggregateRow>.toGroupHeadcountCounts(selected: Set<UUID>): GroupHeadcountCounts {
    check(selected.size in 1..32 && size in 1..6 * (selected.size + 1))
    check(all { it.companyId == null || it.companyId in selected })
    val grouped = groupBy { it.companyId }
    val totals = requireNotNull(grouped[null]).toHeadcountCounts()
    val companies =
        selected.sorted().map { company ->
            CompanyHeadcount(
                company,
                grouped[company]?.toHeadcountCounts() ?: HeadcountCounts(0, 0, 0, 0, 0, 0, 0),
            )
        }
    for (metric in
        listOf(
            HeadcountCounts::employments,
            HeadcountCounts::active,
            HeadcountCounts::probation,
            HeadcountCounts::suspended,
            HeadcountCounts::permanent,
            HeadcountCounts::fixedTerm,
        )) {
        check(
            companies.fold(0L) { sum, company -> Math.addExact(sum, metric(company.counts)) } ==
                metric(totals)
        )
    }
    val maximum = companies.maxOf { it.counts.persons }
    val sum = companies.fold(0L) { sum, company -> Math.addExact(sum, company.counts.persons) }
    check(totals.persons in maximum..sum)
    return GroupHeadcountCounts(totals, companies)
}

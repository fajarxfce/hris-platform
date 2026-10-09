package dev.fajar.hris.reporting.domain.entities

import java.util.Collections

class GroupHeadcountCounts(val totals: HeadcountCounts, companies: List<CompanyHeadcount>) {
    val companies: List<CompanyHeadcount> = Collections.unmodifiableList(companies.toList())
}

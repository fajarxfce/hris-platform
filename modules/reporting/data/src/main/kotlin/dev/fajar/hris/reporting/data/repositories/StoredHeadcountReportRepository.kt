package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.reporting.data.datasources.HeadcountReportDataSource
import dev.fajar.hris.reporting.data.mappers.toGroupHeadcountCounts
import dev.fajar.hris.reporting.domain.entities.HeadcountStatus
import dev.fajar.hris.reporting.domain.repositories.HeadcountReportRepository
import java.time.LocalDate
import java.util.UUID

class StoredHeadcountReportRepository(private val source: HeadcountReportDataSource) :
    HeadcountReportRepository {
    override fun count(companyId: UUID, asOf: LocalDate) = safeDatabaseCall {
        source
            .count(setOf(companyId), asOf, HeadcountStatus.entries.map { it.name }.toSet())
            .toGroupHeadcountCounts(setOf(companyId))
            .totals
    }

    override fun countGroup(companies: Set<UUID>, asOf: LocalDate) = safeDatabaseCall {
        source
            .count(companies, asOf, HeadcountStatus.entries.map { it.name }.toSet())
            .toGroupHeadcountCounts(companies)
    }
}

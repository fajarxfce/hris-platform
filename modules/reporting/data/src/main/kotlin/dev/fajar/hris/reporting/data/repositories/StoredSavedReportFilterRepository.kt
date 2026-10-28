package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.data.datasources.SavedReportFilterDataSource
import dev.fajar.hris.reporting.data.mappers.*
import dev.fajar.hris.reporting.domain.entities.SavedReportFilter
import dev.fajar.hris.reporting.domain.policies.MAX_SAVED_REPORT_FILTERS
import dev.fajar.hris.reporting.domain.repositories.SavedReportFilterRepository
import java.util.UUID

class StoredSavedReportFilterRepository(private val source: SavedReportFilterDataSource) :
    SavedReportFilterRepository {
    override fun lock(company: UUID, account: UUID): Result<Unit> = safeDatabaseCall {
        source.lock(company, account)
    }

    override fun list(company: UUID, account: UUID): Result<List<SavedReportFilter>> =
        safeDatabaseCall {
            val rows = source.list(company, account, MAX_SAVED_REPORT_FILTERS + 1)
            require(
                rows.size <= MAX_SAVED_REPORT_FILTERS &&
                    rows.map { it.id }.toSet().size == rows.size
            )
            rows.map {
                it.toSavedReportFilter(company, account).also { value ->
                    require(value.definition != null)
                }
            }
        }

    override fun countActive(company: UUID, account: UUID): Result<Int> = safeDatabaseCall {
        source.countActive(company, account, MAX_SAVED_REPORT_FILTERS + 1).also {
            require(it in 0..MAX_SAVED_REPORT_FILTERS + 1)
        }
    }

    override fun find(company: UUID, account: UUID, id: UUID): Result<SavedReportFilter?> =
        safeDatabaseCall {
            source.find(company, account, id)?.toSavedReportFilter(company, account)?.also {
                require(it.id == id)
            }
        }

    override fun save(filter: SavedReportFilter, expectedVersion: Long?): Result<Boolean> =
        safeDatabaseCall {
            source.save(filter.toRow(), expectedVersion).also { require(it in 0..1) } == 1
        }
}

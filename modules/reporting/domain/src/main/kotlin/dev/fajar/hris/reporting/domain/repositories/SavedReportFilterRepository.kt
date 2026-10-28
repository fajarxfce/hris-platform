package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.SavedReportFilter
import java.util.UUID

interface SavedReportFilterRepository {
    fun lock(company: UUID, account: UUID): Result<Unit>

    fun list(company: UUID, account: UUID): Result<List<SavedReportFilter>>

    fun countActive(company: UUID, account: UUID): Result<Int>

    fun find(company: UUID, account: UUID, id: UUID): Result<SavedReportFilter?>

    fun save(filter: SavedReportFilter, expectedVersion: Long?): Result<Boolean>
}

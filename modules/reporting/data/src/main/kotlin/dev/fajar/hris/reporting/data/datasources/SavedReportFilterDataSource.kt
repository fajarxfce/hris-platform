package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.SavedReportFilterRow
import java.util.UUID

interface SavedReportFilterDataSource {
    fun lock(company: UUID, account: UUID)

    fun list(company: UUID, account: UUID, limit: Int): List<SavedReportFilterRow>

    fun countActive(company: UUID, account: UUID, limit: Int): Int

    fun find(company: UUID, account: UUID, id: UUID): SavedReportFilterRow?

    fun save(row: SavedReportFilterRow, expectedVersion: Long?): Int
}

package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.HeadcountCounts
import java.time.LocalDate
import java.util.UUID

interface HeadcountReportRepository {
    /** Aggregate in one database snapshot, without materializing employee profiles. */
    fun count(companyId: UUID, asOf: LocalDate): Result<HeadcountCounts>
}

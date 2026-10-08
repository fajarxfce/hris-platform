package dev.fajar.hris.workforce.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.data.datasources.AttendanceCorrectionDataSource
import dev.fajar.hris.workforce.data.mappers.*
import dev.fajar.hris.workforce.domain.entities.AttendanceCorrection
import dev.fajar.hris.workforce.domain.repositories.AttendanceCorrectionRepository
import java.time.LocalDate
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredAttendanceCorrectionRepository(
    private val source: AttendanceCorrectionDataSource,
    private val json: ObjectMapper,
) : AttendanceCorrectionRepository {
    override fun latest(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<AttendanceCorrection>> = safeDatabaseCall {
        source.latest(companyId, employeeId, from, until).map { it.toCorrection(json) }
    }

    override fun history(
        companyId: UUID,
        employeeId: UUID,
        date: LocalDate,
        after: Long?,
        limit: Int,
    ): Result<Page<AttendanceCorrection>> = safeDatabaseCall {
        val rows = source.history(companyId, employeeId, date, after, limit + 1)
        Page(
            rows.take(limit).map { it.toCorrection(json) },
            if (rows.size > limit) rows[limit - 1].revision.toString() else null,
        )
    }

    override fun save(
        companyId: UUID,
        correction: AttendanceCorrection,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) {
                    source.insertVersion(companyId, correction.employeeId, correction.workDate)
                    0L
                } else
                    source.advanceVersion(
                        companyId,
                        correction.employeeId,
                        correction.workDate,
                        expectedVersion,
                    )
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(correction.copy(version = version).toRow(companyId, json))
                    MutationReceipt(correction.id, version)
                }
            }
}

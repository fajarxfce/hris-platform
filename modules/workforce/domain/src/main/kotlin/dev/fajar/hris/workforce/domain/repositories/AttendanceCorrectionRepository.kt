package dev.fajar.hris.workforce.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.AttendanceCorrection
import java.time.LocalDate
import java.util.UUID

interface AttendanceCorrectionRepository {
    fun latest(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<AttendanceCorrection>>

    fun history(
        companyId: UUID,
        employeeId: UUID,
        date: LocalDate,
        after: Long?,
        limit: Int,
    ): Result<Page<AttendanceCorrection>>

    fun save(
        companyId: UUID,
        correction: AttendanceCorrection,
        expectedVersion: Long?,
    ): Result<MutationReceipt>
}

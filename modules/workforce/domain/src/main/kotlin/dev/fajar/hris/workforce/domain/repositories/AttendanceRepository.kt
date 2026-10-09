package dev.fajar.hris.workforce.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface AttendanceRepository {
    fun lockDay(
        companyId: UUID,
        employeeId: UUID,
        date: LocalDate,
        shared: Boolean = false,
    ): Result<Unit>

    fun lockWindows(companyId: UUID, employeeId: UUID): Result<Unit>

    fun findWindow(companyId: UUID, id: UUID): Result<AttendanceCaptureWindow?>

    fun recentWindows(companyId: UUID, employeeId: UUID, since: Instant): Result<Int>

    fun issueWindow(companyId: UUID, window: AttendanceCaptureWindow): Result<Unit>

    fun consumeWindow(companyId: UUID, id: UUID, eventId: UUID): Result<Unit>

    fun find(companyId: UUID, id: UUID): Result<AttendanceEntry?>

    fun entries(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<AttendanceEntry>>

    fun record(companyId: UUID, entry: AttendanceEntry): Result<MutationReceipt>

    fun review(companyId: UUID, id: UUID, review: AttendanceReview): Result<MutationReceipt>
}

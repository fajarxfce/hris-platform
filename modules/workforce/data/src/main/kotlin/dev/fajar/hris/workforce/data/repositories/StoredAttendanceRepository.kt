package dev.fajar.hris.workforce.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.AttendanceReviewsRecord
import dev.fajar.hris.workforce.data.datasources.AttendanceDataSource
import dev.fajar.hris.workforce.data.mappers.*
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.AttendanceRepository
import java.time.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredAttendanceRepository(
    private val source: AttendanceDataSource,
    private val json: ObjectMapper,
) : AttendanceRepository {
    override fun lockDay(
        companyId: UUID,
        employeeId: UUID,
        date: LocalDate,
        shared: Boolean,
    ): Result<Unit> = safeDatabaseCall { source.lockDay(companyId, employeeId, date, shared) }

    override fun lockWindows(companyId: UUID, employeeId: UUID): Result<Unit> = safeDatabaseCall {
        source.lockWindows(companyId, employeeId)
    }

    override fun findWindow(companyId: UUID, id: UUID): Result<AttendanceCaptureWindow?> =
        safeDatabaseCall {
            source.findWindow(companyId, id)?.toWindow()
        }

    override fun recentWindows(companyId: UUID, employeeId: UUID, since: Instant): Result<Int> =
        safeDatabaseCall {
            source.recentWindows(companyId, employeeId, since.atOffset(ZoneOffset.UTC))
        }

    override fun issueWindow(companyId: UUID, window: AttendanceCaptureWindow): Result<Unit> =
        safeDatabaseCall {
            source.insertWindow(window.toRow(companyId))
        }

    override fun consumeWindow(companyId: UUID, id: UUID, eventId: UUID): Result<Unit> =
        safeDatabaseCall { source.consumeWindow(companyId, id, eventId) }
            .flatMap {
                if (it) Result.Success(Unit)
                else Result.Failed(Failure(FailureKind.CONFLICT, "capture_window_consumed"))
            }

    override fun find(companyId: UUID, id: UUID): Result<AttendanceEntry?> = safeDatabaseCall {
        source.find(companyId, id)?.toEntry(json)
    }

    override fun entries(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<AttendanceEntry>> = safeDatabaseCall {
        source.entries(companyId, employeeId, from, until).map { it.toEntry(json) }
    }

    override fun record(companyId: UUID, entry: AttendanceEntry): Result<MutationReceipt> =
        safeDatabaseCall {
            source.insert(entry.toRow(companyId, json))
            MutationReceipt(entry.capture.id, 0)
        }

    override fun review(
        companyId: UUID,
        id: UUID,
        review: AttendanceReview,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.insertReview(
                    AttendanceReviewsRecord().also {
                        it.companyId = companyId
                        it.eventId = id
                        it.actorId = review.actorId
                        it.decision = review.decision.name
                        it.reviewedAt = review.reviewedAt.atOffset(ZoneOffset.UTC)
                        it.reason = review.reason
                    }
                )
            }
            .requireCurrentVersion()
            .map { MutationReceipt(it, 1) }
}

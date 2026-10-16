package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.workforce.delivery.mappers.toResponse
import dev.fajar.hris.workforce.delivery.requests.AttendanceAdjustmentRequest
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.AttendanceEvidenceDecision
import dev.fajar.hris.workforce.domain.usecases.*
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce/employees/{id}/attendance/adjustments")
class AttendanceAdjustmentController(
    private val review: GetAttendanceAdjustmentReview,
    private val adjust: AdjustClosedAttendance,
    private val history: ListAttendanceAdjustments,
    private val details: GetAttendanceAdjustment,
) {
    @GetMapping("/review")
    fun review(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam workDate: LocalDate,
    ): AttendanceAdjustmentReviewResponse =
        review.execute(actor, id, workDate).response().toResponse()

    @PostMapping
    fun adjust(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: AttendanceAdjustmentRequest,
    ): MutationResponse =
        adjust
            .execute(
                actor,
                operationId,
                id,
                body.workDate,
                body.reviewRevision,
                body.clockIn,
                body.clockOut,
                body.breakMinutes,
                body.decisions.map {
                    AttendanceEvidenceDecision(it.eventId, it.expectedVersion, it.decision)
                },
                body.reason,
            )
            .response()
            .toResponse()

    @GetMapping
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam month: YearMonth,
        @RequestParam(required = false) @Parameter(schema = Schema(minimum = "1")) before: Long?,
        @RequestParam(defaultValue = "25")
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        limit: Int,
    ): Page<AttendanceAdjustmentSummaryResponse> =
        history.execute(actor, id, month, before, limit).response().let {
            Page(it.items.map { change -> change.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{adjustmentId}")
    fun details(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable adjustmentId: UUID,
    ): AttendanceAdjustmentDetailsResponse =
        details.execute(actor, id, adjustmentId).response().toResponse()
}

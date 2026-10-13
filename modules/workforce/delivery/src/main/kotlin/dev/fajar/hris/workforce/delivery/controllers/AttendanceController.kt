package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.http.*
import dev.fajar.hris.workforce.delivery.mappers.*
import dev.fajar.hris.workforce.delivery.requests.*
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.AttendanceExceptionKind
import dev.fajar.hris.workforce.domain.usecases.*
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce")
class AttendanceController(
    private val issueWindow: IssueAttendanceCaptureWindow,
    private val record: RecordAttendance,
    private val review: ReviewAttendance,
    private val attendance: GetEmployeeAttendance,
    private val exceptions: ListAttendanceExceptions,
    private val day: GetAttendanceDay,
) {
    @GetMapping("/attendance/days/{id}")
    fun day(actor: Actor, @PathVariable id: UUID): AttendanceRecordResponse =
        day.execute(actor, id).response().toResponse()

    @GetMapping("/attendance/exceptions")
    fun exceptions(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
        @RequestParam(required = false) kind: AttendanceExceptionKind?,
        @Parameter(schema = Schema(maxLength = 160)) @RequestParam(required = false) after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "200", defaultValue = "50"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): Page<AttendanceExceptionResponse> =
        exceptions.execute(actor, from, until, kind, after, limit).response().let {
            Page(it.items.map { entry -> entry.toResponse() }, it.nextCursor)
        }

    @PostMapping("/employees/{id}/attendance/windows")
    fun window(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: AttendanceWindowRequest,
    ): AttendanceWindowResponse =
        issueWindow.execute(actor, operationId, id, body.deviceId).response().toResponse()

    @PostMapping("/employees/{id}/attendance")
    fun record(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: AttendanceCaptureRequest,
    ): MutationResponse =
        record.execute(actor, operationId, body.toCapture(id)).response().toResponse()

    @PostMapping("/attendance/{id}/review")
    fun review(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: AttendanceReviewRequest,
    ): MutationResponse =
        review
            .execute(actor, operationId, id, body.version, body.decision, body.reason)
            .response()
            .toResponse()

    @GetMapping("/employees/{id}/attendance")
    fun entries(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
    ): List<AttendanceDayResponse> =
        attendance.execute(actor, id, from, until).response().map { it.toResponse() }
}

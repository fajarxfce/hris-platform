package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.workforce.delivery.mappers.toResponse
import dev.fajar.hris.workforce.delivery.requests.AttendanceCorrectionRequest
import dev.fajar.hris.workforce.delivery.responses.AttendanceCorrectionResponse
import dev.fajar.hris.workforce.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce/employees/{id}/attendance/corrections")
class AttendanceCorrectionController(
    private val correct: CorrectAttendance,
    private val history: GetAttendanceCorrectionHistory,
) {
    @PostMapping
    fun correct(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: AttendanceCorrectionRequest,
    ): MutationResponse =
        correct
            .execute(
                actor,
                operationId,
                id,
                body.workDate,
                body.clockIn,
                body.clockOut,
                body.breakMinutes,
                body.expectedVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @GetMapping
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam workDate: LocalDate,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<AttendanceCorrectionResponse> =
        history.execute(actor, id, workDate, after, limit).response().let {
            Page(it.items.map { correction -> correction.toResponse() }, it.nextCursor)
        }
}

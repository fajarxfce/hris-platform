package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.leave.delivery.mappers.*
import dev.fajar.hris.leave.delivery.requests.LeaveAccrualScheduleRequest
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.ScheduleLeaveAccrualCommand
import dev.fajar.hris.leave.domain.usecases.*
import java.time.YearMonth
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave/accrual-schedules")
class LeaveAccrualScheduleController(
    private val review: ReviewLeaveAccrualSchedule,
    private val schedule: ScheduleLeaveAccrual,
    private val get: GetLeaveAccrualSchedule,
    private val list: ListLeaveAccrualSchedules,
) {
    @GetMapping("/review")
    fun review(
        actor: Actor,
        @RequestParam typeId: UUID,
        @RequestParam firstPeriod: YearMonth,
        @RequestParam periods: Int,
    ): LeaveAccrualScheduleReviewResponse =
        review.execute(actor, typeId, firstPeriod, periods).response().toResponse()

    @PostMapping
    fun schedule(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveAccrualScheduleRequest,
    ): MutationResponse =
        schedule
            .execute(
                actor,
                operationId,
                ScheduleLeaveAccrualCommand(
                    body.id,
                    body.typeId,
                    body.firstPeriod,
                    body.periods,
                    body.expectedPolicyVersion,
                    body.employeeIds,
                    body.reason,
                ),
            )
            .response()
            .toResponse()

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): LeaveAccrualScheduleDetailsResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): Page<LeaveAccrualScheduleResponse> =
        list.execute(actor, after, limit).response().let {
            Page(it.items.map { value -> value.toResponse() }, it.nextCursor)
        }
}

package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.workforce.delivery.mappers.*
import dev.fajar.hris.workforce.delivery.requests.*
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.WorkHoliday
import dev.fajar.hris.workforce.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce")
class WorkCalendarController(
    private val saveShift: SaveShift,
    private val shifts: ListShifts,
    private val assign: AssignWeeklySchedule,
    private val roster: SetRosterDay,
    private val calendar: GetEmployeeCalendar,
    private val saveHoliday: SaveWorkHoliday,
    private val holidays: ListWorkHolidays,
) {
    @GetMapping("/shifts")
    fun shifts(
        actor: Actor,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ShiftResponse> =
        shifts.execute(actor, query, after, limit).response().let {
            Page(it.items.map { shift -> shift.toResponse() }, it.nextCursor)
        }

    @PutMapping("/shifts/{id}")
    fun saveShift(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: ShiftRequest,
    ): MutationResponse =
        saveShift
            .execute(
                actor,
                operationId,
                id,
                body.toDetails(),
                body.active,
                body.expectedVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @PutMapping("/employees/{id}/schedule")
    fun assign(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: WeeklyScheduleRequest,
    ): MutationResponse =
        assign
            .execute(
                actor,
                operationId,
                id,
                body.effectiveFrom,
                body.days.mapValues { it.value.toReference() },
                body.expectedVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @PutMapping("/employees/{id}/roster/{day}")
    fun roster(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable day: LocalDate,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: RosterDayRequest,
    ): MutationResponse =
        roster
            .execute(
                actor,
                operationId,
                id,
                day,
                body.shift?.toReference(),
                body.expectedVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @GetMapping("/employees/{id}/calendar")
    fun calendar(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
    ): EmployeeCalendarResponse = calendar.execute(actor, id, from, until).response().toResponse()

    @GetMapping("/holidays")
    fun holidays(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
    ): List<WorkHolidayResponse> =
        holidays.execute(actor, from, until).response().map { it.toResponse() }

    @PutMapping("/holidays/{id}")
    fun saveHoliday(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: WorkHolidayRequest,
    ): MutationResponse =
        saveHoliday
            .execute(
                actor,
                operationId,
                WorkHoliday(id, body.workDate, body.name, body.active, body.expectedVersion ?: 0),
                body.expectedVersion,
                body.reason,
            )
            .response()
            .toResponse()
}

package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.workforce.delivery.mappers.toResponse
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce/me")
class OwnedWorkCalendarController(
    private val schedule: GetOwnedWorkSchedule,
    private val roster: GetOwnedRosterDay,
    private val holiday: GetOwnedWorkHoliday,
) {
    @GetMapping("/schedules/{id}")
    fun schedule(actor: Actor, @PathVariable id: UUID): WorkScheduleRecordResponse =
        schedule.execute(actor, id).response().toResponse()

    @GetMapping("/roster/{id}")
    fun roster(actor: Actor, @PathVariable id: UUID): RosterRecordResponse =
        roster.execute(actor, id).response().toResponse()

    @GetMapping("/holidays/{id}")
    fun holiday(actor: Actor, @PathVariable id: UUID): WorkHolidayResponse =
        holiday.execute(actor, id).response().toResponse()
}

package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.leave.delivery.mappers.toResponse
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.LeaveCalendarScope
import dev.fajar.hris.leave.domain.usecases.GetLeaveCalendar
import dev.fajar.hris.leave.domain.usecases.ListLeaveCalendarEntries
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave/calendar")
class LeaveCalendarController(
    private val calendar: GetLeaveCalendar,
    private val entries: ListLeaveCalendarEntries,
) {
    @GetMapping
    fun month(
        actor: Actor,
        @RequestParam scope: LeaveCalendarScope,
        @RequestParam(required = false) month: YearMonth?,
    ): LeaveCalendarResponse = calendar.execute(actor, scope, month).response().toResponse()

    @GetMapping("/entries")
    fun entries(
        actor: Actor,
        @RequestParam scope: LeaveCalendarScope,
        @RequestParam date: LocalDate,
        @RequestParam(required = false) after: UUID?,
        @Parameter(schema = Schema(minimum = "1", maximum = "200"))
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): LeaveCalendarEntriesResponse =
        entries.execute(actor, scope, date, after, limit).response().toResponse()
}

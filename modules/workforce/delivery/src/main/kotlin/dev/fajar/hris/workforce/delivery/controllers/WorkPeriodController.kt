package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.http.*
import dev.fajar.hris.workforce.delivery.mappers.*
import dev.fajar.hris.workforce.delivery.requests.WorkPeriodCommand
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.usecases.*
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.YearMonth
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce/periods")
class WorkPeriodController(
    private val list: ListWorkPeriods,
    private val start: StartWorkPeriodClose,
    private val recover: RecoverWorkPeriod,
    private val snapshot: GetWorkPeriodSnapshot,
    private val overview: GetWorkPeriodOverview,
    private val employees: ListWorkPeriodEmployees,
) {
    @GetMapping("/{month}")
    fun overview(actor: Actor, @PathVariable month: YearMonth): WorkPeriodOverviewResponse =
        overview.execute(actor, month).response().toResponse()

    @GetMapping("/{month}/employees")
    fun employees(
        actor: Actor,
        @PathVariable month: YearMonth,
        @Parameter(schema = Schema(maxLength = 120)) @RequestParam(defaultValue = "") query: String,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): Page<WorkforceEmployeeResponse> =
        employees.execute(actor, month, query, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam from: YearMonth,
        @RequestParam until: YearMonth,
    ): List<WorkPeriodResponse> =
        list.execute(actor, from, until).response().map { it.toResponse() }

    @PostMapping("/{month}/close")
    fun close(
        actor: Actor,
        @PathVariable month: YearMonth,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: WorkPeriodCommand,
    ): MutationResponse =
        start
            .execute(actor, operationId, month, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @PostMapping("/{month}/recover")
    fun recover(
        actor: Actor,
        @PathVariable month: YearMonth,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: WorkPeriodCommand,
    ): MutationResponse =
        recover
            .execute(actor, operationId, month, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/{month}/employees/{employeeId}")
    fun snapshot(
        actor: Actor,
        @PathVariable month: YearMonth,
        @PathVariable employeeId: UUID,
    ): WorkPeriodSnapshotResponse =
        snapshot.execute(actor, month, employeeId).response().toResponse()
}

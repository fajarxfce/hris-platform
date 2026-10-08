package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.workforce.delivery.mappers.*
import dev.fajar.hris.workforce.delivery.requests.WorkPeriodCommand
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.usecases.*
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
) {
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

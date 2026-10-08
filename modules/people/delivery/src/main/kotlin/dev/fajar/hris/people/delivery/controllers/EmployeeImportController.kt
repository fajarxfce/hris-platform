package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.mappers.*
import dev.fajar.hris.people.delivery.requests.*
import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employee-imports")
class EmployeeImportController(
    private val start: StartEmployeeImport,
    private val apply: ApplyEmployeeImport,
    private val resume: ResumeEmployeeImport,
    private val cancel: CancelEmployeeImport,
    private val get: GetEmployeeImport,
    private val list: ListEmployeeImports,
    private val rows: GetEmployeeImportRows,
    private val attempts: GetEmployeeImportAttempts,
    private val template: GetEmployeeImportTemplate,
) {
    @PostMapping
    fun start(
        actor: Actor,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: EmployeeImportRequest,
    ): MutationResponse =
        start
            .execute(actor, key, body.id, body.fileName, body.csv, body.reason)
            .response()
            .toResponse()

    @PostMapping("/{id}/apply")
    fun apply(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: EmployeeImportApplyRequest,
    ): MutationResponse =
        apply
            .execute(actor, key, id, body.expectedVersion, body.allowPartial, body.reason)
            .response()
            .toResponse()

    @PostMapping("/{id}/resume")
    fun resume(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: EmployeeImportChangeRequest,
    ): MutationResponse =
        resume.execute(actor, key, id, body.expectedVersion, body.reason).response().toResponse()

    @PostMapping("/{id}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: EmployeeImportChangeRequest,
    ): MutationResponse =
        cancel.execute(actor, key, id, body.expectedVersion, body.reason).response().toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<EmployeeImportResponse> =
        list.execute(actor, after, limit).response().let {
            Page(it.items.map { batch -> batch.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): EmployeeImportSummaryResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping("/{id}/rows")
    fun rows(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Int?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<EmployeeImportRowResponse> =
        rows.execute(actor, id, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}/attempts")
    fun attempts(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<EmployeeImportAttemptResponse> =
        attempts.execute(actor, id, after, limit).response().let {
            Page(it.items.map { attempt -> attempt.toResponse() }, it.nextCursor)
        }

    @GetMapping("/template", produces = ["text/csv;charset=UTF-8"])
    fun template(actor: Actor): ResponseEntity<String> =
        ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"employee-import-template.csv\"")
            .header("Cache-Control", "no-store")
            .body(template.execute(actor).response())
}

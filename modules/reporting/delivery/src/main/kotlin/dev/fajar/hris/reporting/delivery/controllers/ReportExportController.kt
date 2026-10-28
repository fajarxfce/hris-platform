package dev.fajar.hris.reporting.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.reporting.delivery.requests.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.REPORT_CSV_MAX_PART_BYTES
import dev.fajar.hris.reporting.domain.usecases.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/reports/exports")
class ReportExportController(
    private val read: ReadReportExports,
    private val manage: ManageReportExports,
    private val writer: BinaryResponseWriter,
) {
    @GetMapping
    @CompanyAvailabilityExempt
    fun list(
        actor: Actor,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) beforeAt: Instant?,
        @RequestParam(required = false) beforeId: UUID?,
    ): ReportExportHistory {
        if ((beforeAt == null) != (beforeId == null))
            throw DomainFailureException(Failure(FailureKind.VALIDATION, "invalid_pagination"))
        return read
            .list(actor, size, beforeAt?.let { ReportExportCursor(it, requireNotNull(beforeId)) })
            .response()
    }

    @GetMapping("/{exportId}")
    @CompanyAvailabilityExempt
    fun get(actor: Actor, @PathVariable exportId: UUID): ReportExportDetails =
        read.get(actor, exportId).response()

    @PostMapping(consumes = ["application/json"])
    fun start(
        actor: Actor,
        client: ClientRequest,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: StartReportExportRequest,
    ): MutationResponse {
        val p = input.parameters
        return manage
            .start(
                actor,
                operationId,
                ReportSelection(
                    input.kind,
                    input.definitionVersion,
                    ReportFilterParameters(p.asOf, p.from, p.until, p.status, p.companies),
                ),
                input.dataset,
                client,
            )
            .response()
            .toResponse()
    }

    @PostMapping("/{exportId}/cancel", consumes = ["application/json"])
    @CompanyAvailabilityExempt
    fun cancel(
        actor: Actor,
        @PathVariable exportId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: ChangeReportExportRequest,
    ): MutationResponse =
        manage.cancel(actor, operationId, exportId, input.expectedVersion).response().toResponse()

    @PostMapping("/{exportId}/resume", consumes = ["application/json"])
    fun resume(
        actor: Actor,
        client: ClientRequest,
        @PathVariable exportId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: ChangeReportExportRequest,
    ): MutationResponse =
        manage
            .resume(actor, operationId, exportId, input.expectedVersion, client)
            .response()
            .toResponse()

    @GetMapping("/{exportId}/content")
    fun download(
        actor: Actor,
        client: ClientRequest,
        @PathVariable exportId: UUID,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val content = read.download(actor, exportId, client).response()
        writeBinaryDownloadResponse(
            request,
            response,
            BinaryDownloadMetadata(
                content.fileName,
                "text/csv; charset=UTF-8",
                content.bytes,
                "\"${content.id}-${content.tag}\"",
            ),
            writer,
            REPORT_CSV_MAX_PART_BYTES,
        ) { offset, length ->
            read.read(actor, exportId, content.tag, offset, length, client)
        }
    }
}

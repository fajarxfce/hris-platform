package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.usecases.ListLeavePreparationEmployees
import dev.fajar.hris.leave.domain.usecases.ListLeavePreparationEvidence
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave/preparation/employees")
class LeavePreparationController(
    private val employees: ListLeavePreparationEmployees,
    private val evidence: ListLeavePreparationEvidence,
) {
    @GetMapping
    fun employees(
        actor: Actor,
        @Parameter(schema = Schema(maxLength = 120)) @RequestParam(defaultValue = "") query: String,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): LeavePreparationDirectoryResponse =
        employees.execute(actor, query, after, limit).response().let { directory ->
            LeavePreparationDirectoryResponse(
                directory.companyDate,
                directory.timezone,
                Page(
                    directory.employees.items.map {
                        LeavePreparationEmployeeResponse(
                            it.id,
                            it.employeeNumber,
                            it.legalName,
                            it.status.name,
                            it.version,
                        )
                    },
                    directory.employees.nextCursor,
                ),
            )
        }

    @GetMapping("/{employeeId}/evidence")
    fun evidence(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @RequestParam(required = false) after: UUID?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): Page<LeaveEvidenceOptionResponse> =
        evidence.execute(actor, employeeId, after, limit).response().let { page ->
            Page(
                page.items.map {
                    LeaveEvidenceOptionResponse(
                        it.documentId,
                        it.revisionId,
                        it.revisionNumber,
                        it.title,
                        it.fileName,
                        it.mediaType,
                        it.size,
                        it.validatedAt,
                    )
                },
                page.nextCursor,
            )
        }
}

package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.leave.delivery.mappers.toResponse
import dev.fajar.hris.leave.delivery.requests.LeavePreviewRequest
import dev.fajar.hris.leave.delivery.responses.LeaveRequestPreviewResponse
import dev.fajar.hris.leave.delivery.responses.LeaveRequestResponse
import dev.fajar.hris.leave.domain.entities.LeavePreparationScope
import dev.fajar.hris.leave.domain.entities.LeaveRequestReadScope
import dev.fajar.hris.leave.domain.entities.RequestedLeaveDay
import dev.fajar.hris.leave.domain.usecases.GetLeaveRequest
import dev.fajar.hris.leave.domain.usecases.PreviewLeaveRequest
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave/me/requests")
class SelfLeaveRequestController(
    private val details: GetLeaveRequest,
    private val preview: PreviewLeaveRequest,
) {
    @PostMapping("/preview")
    fun preview(actor: Actor, @RequestBody body: LeavePreviewRequest): LeaveRequestPreviewResponse =
        preview
            .execute(
                actor,
                body.employeeId,
                body.typeId,
                body.days.map { RequestedLeaveDay(it.workDate, it.portion) },
                LeavePreparationScope.SELF,
            )
            .response()
            .toResponse()

    @GetMapping("/{id}")
    fun details(
        actor: Actor,
        @PathVariable id: UUID,
        @Parameter(schema = Schema(minimum = "0"))
        @RequestParam(required = false)
        historyAfter: Long?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50", defaultValue = "10"))
        @RequestParam(defaultValue = "10")
        historyLimit: Int,
    ): LeaveRequestResponse =
        details
            .execute(actor, id, historyAfter, historyLimit, LeaveRequestReadScope.SELF)
            .response()
            .toResponse()
}

package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.leave.delivery.mappers.*
import dev.fajar.hris.leave.delivery.requests.*
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave")
class LeaveEntitlementController(
    private val post: PostEmployeeLeaveAccrual,
    private val close: CloseEmployeeLeaveYear,
    private val account: GetLeaveAccount,
    private val entitlements: GetLeaveEntitlements,
) {
    @PostMapping("/employees/{employeeId}/accruals/{typeId}")
    fun post(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable typeId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveAccrualRequest,
    ): MutationResponse =
        post
            .execute(
                actor,
                operationId,
                body.id,
                employeeId,
                typeId,
                body.month,
                body.expectedEmploymentVersion,
                body.expectedPolicyVersion,
                body.expectedBalanceVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/employees/{employeeId}/balances/{typeId}/{year}/closing")
    fun close(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable typeId: UUID,
        @PathVariable year: Int,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveYearCloseRequest,
    ): MutationResponse =
        close
            .execute(
                actor,
                operationId,
                body.id,
                employeeId,
                typeId,
                year,
                body.expectedPolicyVersion,
                body.expectedBalanceVersion,
                body.expectedDestinationVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @GetMapping("/employees/{employeeId}/balances/{typeId}/{year}/entitlements")
    fun entitlements(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable typeId: UUID,
        @PathVariable year: Int,
    ): LeaveEntitlementsResponse =
        entitlements.execute(actor, employeeId, typeId, year).response().toResponse()

    @GetMapping("/balances/{id}")
    fun account(actor: Actor, @PathVariable id: UUID): LeaveAccountResponse =
        account.execute(actor, id).response().toResponse()
}

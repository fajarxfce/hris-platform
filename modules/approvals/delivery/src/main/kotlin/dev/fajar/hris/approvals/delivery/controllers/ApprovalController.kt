package dev.fajar.hris.approvals.delivery.controllers

import dev.fajar.hris.approvals.delivery.mappers.toResponse
import dev.fajar.hris.approvals.delivery.requests.*
import dev.fajar.hris.approvals.delivery.responses.*
import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/approvals")
class ApprovalController(
    private val templates: ListApprovalTemplates,
    private val saveTemplate: SaveApprovalTemplate,
    private val inbox: ListApprovalInbox,
    private val get: GetApprovalRequest,
    private val reassign: ReassignApproval,
    private val delegations: ListMyDelegations,
    private val saveDelegation: SaveApprovalDelegation,
    private val getTemplate: GetApprovalTemplate,
    private val getDelegation: GetApprovalDelegation,
    private val assignees: ListApprovalAssignees,
) {
    @GetMapping("/templates/{id}")
    fun template(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) revision: Long?,
    ): TemplateResponse = getTemplate.execute(actor, id, revision).response().toResponse()

    @GetMapping("/delegations/{id}")
    fun delegation(actor: Actor, @PathVariable id: UUID): DelegationResponse =
        getDelegation.execute(actor, id).response().toResponse()

    @GetMapping("/assignees")
    fun assignees(
        actor: Actor,
        @RequestParam kind: ApprovalKind,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ApprovalAssigneeResponse> =
        assignees.execute(actor, kind, query, after, limit).response().let { page ->
            Page(
                page.items.map { ApprovalAssigneeResponse(it.id, it.displayName) },
                page.nextCursor,
            )
        }

    @GetMapping("/templates")
    fun templates(
        actor: Actor,
        @RequestParam kind: ApprovalKind,
        @RequestParam asOf: LocalDate,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<TemplateResponse> =
        templates.execute(actor, kind, asOf, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @PutMapping("/templates/{id}")
    fun saveTemplate(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: TemplateRequest,
    ): MutationResponse =
        saveTemplate
            .execute(
                actor,
                operationId,
                TemplateChange(
                    id,
                    body.name,
                    body.kind,
                    body.active,
                    body.expectedVersion,
                    body.effectiveFrom,
                    body.category,
                    decimalAmount(body.minimumAmount),
                    body.stages.map { StageRule(it.assignment, it.accountIds, it.permission) },
                    body.reason,
                ),
            )
            .response()
            .toResponse()

    @GetMapping
    fun inbox(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ApprovalResponse> =
        inbox.execute(actor, after, limit).response().let {
            Page(it.items.map { request -> request.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): ApprovalResponse =
        get.execute(actor, id).response().toResponse()

    @PostMapping("/{id}/reassign")
    fun reassign(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: ReassignmentRequest,
    ): MutationResponse =
        reassign
            .execute(actor, operationId, id, body.version, body.assignees, body.reason)
            .response()
            .toResponse()

    @GetMapping("/delegations")
    fun delegations(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<DelegationResponse> =
        delegations.execute(actor, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @PutMapping("/delegations/{id}")
    fun saveDelegation(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: DelegationRequest,
    ): MutationResponse =
        saveDelegation
            .execute(
                actor,
                operationId,
                Delegation(
                    id,
                    body.kind,
                    body.fromAccount,
                    body.toAccount,
                    body.validFrom,
                    body.validUntil,
                    body.active,
                    body.expectedVersion ?: 0,
                ),
                body.expectedVersion,
                body.reason,
            )
            .response()
            .toResponse()
}

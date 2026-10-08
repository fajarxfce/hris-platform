package dev.fajar.hris.identity.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.identity.delivery.requests.MembershipRequest
import dev.fajar.hris.identity.delivery.responses.*
import dev.fajar.hris.identity.domain.entities.RoleTemplateSelection
import dev.fajar.hris.identity.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/members")
class MembershipController(
    private val list: ListCompanyMembers,
    private val save: SaveCompanyMembership,
    private val getGrant: GetCompanyMemberGrant,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<MemberResponse> =
        list.execute(actor, after, limit).response().let { page ->
            Page(
                page.items.map {
                    MemberResponse(
                        it.id,
                        it.email,
                        it.displayName,
                        it.accountActive,
                        it.membershipActive,
                        it.permissions,
                        it.version,
                    )
                },
                page.nextCursor,
            )
        }

    @GetMapping("/{accountId}")
    fun get(actor: Actor, @PathVariable accountId: UUID): MemberGrantResponse {
        val result = getGrant.execute(actor, accountId).response()
        val member = result.member
        return MemberGrantResponse(
            MemberResponse(
                member.id,
                member.email,
                member.displayName,
                member.accountActive,
                member.membershipActive,
                member.permissions,
                member.version,
            ),
            result.grant.directPermissions,
            result.grant.roleTemplates.map {
                AppliedRoleTemplateResponse(it.id, it.code, it.name, it.permissions, it.version)
            },
        )
    }

    @PutMapping("/{accountId}")
    fun save(
        actor: Actor,
        @PathVariable accountId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: MembershipRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                accountId,
                body.expectedVersion,
                body.active,
                body.permissions,
                body.reason,
                body.roleTemplates.map { RoleTemplateSelection(it.id, it.version) },
            )
            .response()
            .toResponse()
}

package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.mappers.*
import dev.fajar.hris.people.delivery.requests.*
import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.LifecycleStatus
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/lifecycle")
class LifecycleCaseController(
    private val start: StartLifecycleCase,
    private val get: GetLifecycleCase,
    private val list: ListLifecycleCases,
    private val history: GetLifecycleHistory,
    private val cancel: CancelLifecycleCase,
    private val completeOnboarding: CompleteOnboarding,
    private val completeOffboarding: CompleteOffboarding,
) {
    @PostMapping("/cases")
    fun start(
        actor: Actor,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: StartLifecycleRequest,
    ): MutationResponse =
        start.execute(actor, key, body.id, body.toCommand()).response().toResponse()

    @GetMapping("/cases")
    fun list(
        actor: Actor,
        @RequestParam(required = false) employmentId: UUID?,
        @RequestParam(required = false) status: LifecycleStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<LifecycleCaseResponse> =
        list.execute(actor, employmentId, status, after, limit).response().let {
            Page(it.items.map { case -> case.toResponse() }, it.nextCursor)
        }

    @GetMapping("/cases/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): LifecycleCaseResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping("/cases/{id}/history")
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<LifecycleEventResponse> =
        history.execute(actor, id, after, limit).response().let {
            Page(it.items.map { event -> event.toResponse() }, it.nextCursor)
        }

    @PostMapping("/cases/{id}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: LifecycleCompletionRequest,
    ): MutationResponse =
        cancel.execute(actor, key, id, body.expectedVersion, body.reason).response().toResponse()

    @PostMapping("/cases/{id}/complete-onboarding")
    fun completeOnboarding(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: LifecycleCompletionRequest,
    ): MutationResponse =
        completeOnboarding
            .execute(actor, key, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @PostMapping("/cases/{id}/complete-offboarding")
    fun completeOffboarding(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: OffboardingCompletionRequest,
    ): MutationResponse =
        completeOffboarding
            .execute(actor, key, id, body.expectedVersion, body.employmentVersion, body.reason)
            .response()
            .toResponse()
}

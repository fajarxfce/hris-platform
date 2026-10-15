package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.mappers.*
import dev.fajar.hris.people.delivery.requests.*
import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.ScheduleOffboardingCommand
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/lifecycle/cases/{caseId}/offboarding-schedules")
class OffboardingScheduleController(
    private val review: GetOffboardingScheduleReview,
    private val list: ListOffboardingSchedules,
    private val get: GetOffboardingSchedule,
    private val schedule: ScheduleOffboarding,
    private val cancel: CancelOffboardingSchedule,
    private val recover: RecoverOffboardingSchedule,
) {
    @GetMapping("/review")
    fun review(actor: Actor, @PathVariable caseId: UUID): OffboardingScheduleReviewResponse =
        review.execute(actor, caseId).response().toResponse()

    @GetMapping
    fun list(actor: Actor, @PathVariable caseId: UUID): Page<OffboardingScheduleResponse> =
        Page(list.execute(actor, caseId).response().map { it.toResponse() }, null)

    @GetMapping("/{id}")
    fun get(
        actor: Actor,
        @PathVariable caseId: UUID,
        @PathVariable id: UUID,
    ): OffboardingScheduleResponse = get.execute(actor, caseId, id).response().toResponse()

    @PostMapping
    fun schedule(
        actor: Actor,
        @PathVariable caseId: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: OffboardingScheduleRequest,
    ): MutationResponse =
        schedule
            .execute(
                actor,
                key,
                ScheduleOffboardingCommand(
                    body.id,
                    caseId,
                    body.expectedVersion,
                    body.employmentVersion,
                    body.basisRevision,
                    body.reason,
                ),
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable caseId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: OffboardingScheduleJobRequest,
    ): MutationResponse =
        cancel
            .execute(
                actor,
                key,
                caseId,
                id,
                body.expectedVersion,
                body.expectedJobVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/recover")
    fun recover(
        actor: Actor,
        @PathVariable caseId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: OffboardingScheduleJobRequest,
    ): MutationResponse =
        recover
            .execute(
                actor,
                key,
                caseId,
                id,
                body.expectedVersion,
                body.expectedJobVersion,
                body.reason,
            )
            .response()
            .toResponse()
}

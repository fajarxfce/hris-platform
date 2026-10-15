package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.mappers.*
import dev.fajar.hris.people.delivery.requests.*
import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.OnboardingScheduleSelection
import dev.fajar.hris.people.domain.entities.ScheduleOnboardingCommand
import dev.fajar.hris.people.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees/{employmentId}/onboarding-schedules")
class OnboardingScheduleController(
    private val review: GetOnboardingScheduleReview,
    private val list: ListOnboardingSchedules,
    private val get: GetOnboardingSchedule,
    private val schedule: ScheduleOnboarding,
    private val cancel: CancelOnboardingSchedule,
    private val recover: RecoverOnboardingSchedule,
) {
    @GetMapping("/review")
    fun review(
        actor: Actor,
        @PathVariable employmentId: UUID,
        @RequestParam templateId: UUID,
        @RequestParam targetDate: LocalDate,
        @RequestParam startsOn: LocalDate,
    ): OnboardingScheduleReviewResponse =
        review
            .execute(
                actor,
                employmentId,
                OnboardingScheduleSelection(templateId, targetDate, startsOn),
            )
            .response()
            .toResponse()

    @GetMapping
    fun list(actor: Actor, @PathVariable employmentId: UUID): Page<OnboardingScheduleResponse> =
        Page(list.execute(actor, employmentId).response().map { it.toResponse() }, null)

    @GetMapping("/{id}")
    fun get(
        actor: Actor,
        @PathVariable employmentId: UUID,
        @PathVariable id: UUID,
    ): OnboardingScheduleResponse = get.execute(actor, employmentId, id).response().toResponse()

    @PostMapping
    fun schedule(
        actor: Actor,
        @PathVariable employmentId: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: OnboardingScheduleRequest,
    ): MutationResponse =
        schedule
            .execute(
                actor,
                key,
                ScheduleOnboardingCommand(
                    body.id,
                    body.caseId,
                    employmentId,
                    OnboardingScheduleSelection(body.templateId, body.targetDate, body.startsOn),
                    body.employmentVersion,
                    body.templateVersion,
                    body.basisRevision,
                    body.assignees,
                    body.reason,
                ),
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable employmentId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: OnboardingScheduleJobRequest,
    ): MutationResponse =
        cancel
            .execute(
                actor,
                key,
                employmentId,
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
        @PathVariable employmentId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: OnboardingScheduleJobRequest,
    ): MutationResponse =
        recover
            .execute(
                actor,
                key,
                employmentId,
                id,
                body.expectedVersion,
                body.expectedJobVersion,
                body.reason,
            )
            .response()
            .toResponse()
}

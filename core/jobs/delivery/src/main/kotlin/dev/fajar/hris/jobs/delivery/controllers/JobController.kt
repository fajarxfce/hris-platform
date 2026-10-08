package dev.fajar.hris.jobs.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.jobs.delivery.mappers.toResponse
import dev.fajar.hris.jobs.delivery.requests.CancelJobRequest
import dev.fajar.hris.jobs.delivery.responses.*
import dev.fajar.hris.jobs.domain.usecases.*
import java.time.Instant
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/jobs")
class JobController(
    private val list: ListJobs,
    private val get: GetJob,
    private val cancel: RequestJobCancellation,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) beforeAt: Instant?,
        @RequestParam(required = false) beforeId: UUID?,
    ): JobPageResponse = list.execute(actor, size, beforeAt, beforeId).response().toResponse(actor)

    @GetMapping("/{jobId}")
    fun get(actor: Actor, @PathVariable jobId: UUID): JobResponse =
        get.execute(actor, jobId).response().toResponse(actor)

    @PostMapping("/{jobId}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable jobId: UUID,
        @RequestBody input: CancelJobRequest,
    ): JobResponse =
        cancel.execute(actor, jobId, input.expectedVersion).response().toResponse(actor)
}

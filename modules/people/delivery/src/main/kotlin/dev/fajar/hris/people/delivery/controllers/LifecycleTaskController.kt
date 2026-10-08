package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.mappers.*
import dev.fajar.hris.people.delivery.requests.*
import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/lifecycle")
class LifecycleTaskController(
    private val change: ChangeLifecycleTask,
    private val assign: AssignLifecycleTask,
    private val assigned: ListAssignedLifecycleTasks,
) {
    @PutMapping("/cases/{id}/tasks/{taskKey}")
    fun change(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable taskKey: String,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: LifecycleTaskChangeRequest,
    ): MutationResponse =
        change.execute(actor, key, id, taskKey, body.toCommand()).response().toResponse()

    @PutMapping("/cases/{id}/tasks/{taskKey}/assignee")
    fun assign(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable taskKey: String,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: LifecycleAssignmentRequest,
    ): MutationResponse =
        assign
            .execute(actor, key, id, taskKey, body.expectedVersion, body.assigneeId, body.reason)
            .response()
            .toResponse()

    @GetMapping("/tasks/assigned")
    fun assigned(
        actor: Actor,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<AssignedLifecycleTaskResponse> =
        assigned.execute(actor, after, limit).response().let {
            Page(it.items.map { task -> task.toResponse() }, it.nextCursor)
        }
}

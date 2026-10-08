package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.*

fun LifecycleTemplate.toResponse() =
    LifecycleTemplateResponse(
        id,
        code,
        name,
        kind.name,
        active,
        version,
        tasks.map { LifecycleTemplateTaskResponse(it.key, it.title, it.required, it.dueDays) },
    )

fun LifecycleTask.toResponse() =
    LifecycleTaskResponse(
        key,
        title,
        required,
        dueDate,
        assigneeId,
        status.name,
        completedBy,
        completedAt,
    )

fun LifecycleCase.toResponse() =
    LifecycleCaseResponse(
        id,
        employmentId,
        kind.name,
        targetDate,
        templateId,
        templateVersion,
        templateName,
        status.name,
        version,
        createdBy,
        createdAt,
        tasks.map { it.toResponse() },
    )

fun LifecycleEvent.toResponse() =
    LifecycleEventResponse(version, taskKey, action.name, assigneeId, actorId, reason, recordedAt)

fun AssignedLifecycleTask.toResponse() =
    AssignedLifecycleTaskResponse(caseId, employmentId, kind.name, caseVersion, task.toResponse())

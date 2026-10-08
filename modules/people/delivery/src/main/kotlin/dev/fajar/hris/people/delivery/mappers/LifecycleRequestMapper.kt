package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.requests.*
import dev.fajar.hris.people.domain.entities.*
import java.util.UUID

fun LifecycleTemplateRequest.toTemplate(id: UUID) =
    LifecycleTemplate(
        id,
        code,
        name,
        kind,
        active,
        expectedVersion ?: 0,
        tasks.map { LifecycleTaskDefinition(it.key, it.title, it.required, it.dueDays) },
    )

fun StartLifecycleRequest.toCommand() =
    StartLifecycleCommand(employmentId, templateId, templateVersion, targetDate, assignees, reason)

fun LifecycleTaskChangeRequest.toCommand() = LifecycleTaskChange(expectedVersion, status, reason)

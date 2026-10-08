package dev.fajar.hris.people.delivery.requests

import dev.fajar.hris.people.domain.entities.LifecycleKind

data class LifecycleTemplateRequest(
    val code: String,
    val name: String,
    val kind: LifecycleKind,
    val active: Boolean,
    val expectedVersion: Long?,
    val tasks: List<LifecycleTemplateTaskRequest>,
    val reason: String,
)

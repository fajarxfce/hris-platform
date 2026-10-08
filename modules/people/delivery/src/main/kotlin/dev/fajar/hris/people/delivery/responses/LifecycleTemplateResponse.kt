package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class LifecycleTemplateResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val kind: String,
    val active: Boolean,
    val version: Long,
    val tasks: List<LifecycleTemplateTaskResponse>,
)

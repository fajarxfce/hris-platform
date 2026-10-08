package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class LifecycleTemplate(
    val id: UUID,
    val code: String,
    val name: String,
    val kind: LifecycleKind,
    val active: Boolean,
    val version: Long,
    val tasks: List<LifecycleTaskDefinition>,
)

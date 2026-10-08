package dev.fajar.hris.people.domain.entities

data class LifecycleTaskChange(
    val expectedVersion: Long,
    val status: LifecycleTaskStatus,
    val reason: String,
)

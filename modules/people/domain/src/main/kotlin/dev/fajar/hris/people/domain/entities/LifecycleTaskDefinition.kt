package dev.fajar.hris.people.domain.entities

data class LifecycleTaskDefinition(
    val key: String,
    val title: String,
    val required: Boolean,
    val dueDays: Int,
)

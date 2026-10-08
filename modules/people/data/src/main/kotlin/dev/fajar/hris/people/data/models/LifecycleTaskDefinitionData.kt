package dev.fajar.hris.people.data.models

data class LifecycleTaskDefinitionData(
    val key: String,
    val title: String,
    val required: Boolean,
    val dueDays: Int,
)

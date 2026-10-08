package dev.fajar.hris.people.delivery.responses

data class LifecycleTemplateTaskResponse(
    val key: String,
    val title: String,
    val required: Boolean,
    val dueDays: Int,
)

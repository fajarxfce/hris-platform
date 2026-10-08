package dev.fajar.hris.people.delivery.requests

data class LifecycleTemplateTaskRequest(
    val key: String,
    val title: String,
    val required: Boolean,
    val dueDays: Int,
)

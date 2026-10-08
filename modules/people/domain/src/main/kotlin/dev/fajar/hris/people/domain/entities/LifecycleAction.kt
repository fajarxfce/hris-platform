package dev.fajar.hris.people.domain.entities

enum class LifecycleAction {
    CREATED,
    ASSIGNED,
    TASK_PENDING,
    TASK_DONE,
    TASK_WAIVED,
    CANCELLED,
    COMPLETED,
}

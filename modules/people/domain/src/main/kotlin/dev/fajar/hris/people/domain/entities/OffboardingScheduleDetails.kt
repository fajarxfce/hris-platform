package dev.fajar.hris.people.domain.entities

data class OffboardingScheduleDetails(
    val schedule: OffboardingSchedule,
    val job: LifecycleScheduleJob,
    val actions: Set<String> = emptySet(),
)

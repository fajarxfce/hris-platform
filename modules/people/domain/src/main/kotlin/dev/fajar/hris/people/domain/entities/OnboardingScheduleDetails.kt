package dev.fajar.hris.people.domain.entities

data class OnboardingScheduleDetails(
    val schedule: OnboardingSchedule,
    val job: LifecycleScheduleJob,
    val actions: Set<String> = emptySet(),
)

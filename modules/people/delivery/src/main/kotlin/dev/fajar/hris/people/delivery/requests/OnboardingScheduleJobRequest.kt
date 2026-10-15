package dev.fajar.hris.people.delivery.requests

data class OnboardingScheduleJobRequest(
    val expectedVersion: Long,
    val expectedJobVersion: Long,
    val reason: String,
)

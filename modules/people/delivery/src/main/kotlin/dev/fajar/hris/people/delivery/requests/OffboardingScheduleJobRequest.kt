package dev.fajar.hris.people.delivery.requests

data class OffboardingScheduleJobRequest(
    val expectedVersion: Long,
    val expectedJobVersion: Long,
    val reason: String,
)

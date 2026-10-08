package dev.fajar.hris.people.delivery.requests

data class OffboardingCompletionRequest(
    val expectedVersion: Long,
    val employmentVersion: Long,
    val reason: String,
)

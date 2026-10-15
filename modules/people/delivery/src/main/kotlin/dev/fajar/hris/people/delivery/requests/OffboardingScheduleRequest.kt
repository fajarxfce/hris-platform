package dev.fajar.hris.people.delivery.requests

import java.util.UUID

data class OffboardingScheduleRequest(
    val id: UUID,
    val expectedVersion: Long,
    val employmentVersion: Long,
    val basisRevision: String,
    val reason: String,
)

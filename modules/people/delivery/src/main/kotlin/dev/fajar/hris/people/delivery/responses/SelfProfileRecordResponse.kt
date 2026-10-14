package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class SelfProfileRecordResponse(
    val id: UUID,
    val version: Long,
    val timezone: String,
    val employment: EmploymentDetailsResponse,
    val profile: PersonProfileResponse,
)

package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class PersonSummaryResponse(
    val id: UUID,
    val accountId: UUID?,
    val legalName: String,
    val email: String?,
)

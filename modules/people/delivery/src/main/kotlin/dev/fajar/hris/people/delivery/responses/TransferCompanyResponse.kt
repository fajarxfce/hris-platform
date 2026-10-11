package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class TransferCompanyResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val timezone: String,
)

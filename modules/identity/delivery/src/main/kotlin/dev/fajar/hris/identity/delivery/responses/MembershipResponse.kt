package dev.fajar.hris.identity.delivery.responses

data class MembershipResponse(
    val id: String,
    val name: String,
    val code: String,
    val timezone: String,
)

package dev.fajar.hris.identity.delivery.responses

data class SessionResponse(
    val account: AccountResponse,
    val permissions: List<String>,
    val companies: List<MembershipResponse>,
    val assurance: SessionAssuranceResponse,
)

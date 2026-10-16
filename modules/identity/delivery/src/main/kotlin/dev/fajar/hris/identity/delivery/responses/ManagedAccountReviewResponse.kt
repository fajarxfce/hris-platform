package dev.fajar.hris.identity.delivery.responses

data class ManagedAccountReviewResponse(
    val account: ManagedAccountResponse,
    val assignablePermissions: Set<String>,
)

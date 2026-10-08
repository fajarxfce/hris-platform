package dev.fajar.hris.identity.delivery.requests

data class MembershipRequest(
    val expectedVersion: Long? = null,
    val active: Boolean = true,
    val permissions: Set<String>,
    val reason: String,
)

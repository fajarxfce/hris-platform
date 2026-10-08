package dev.fajar.hris.identity.delivery.requests

data class RoleTemplateRequest(
    val expectedVersion: Long? = null,
    val code: String,
    val name: String,
    val permissions: Set<String>,
    val active: Boolean = true,
    val reason: String,
)

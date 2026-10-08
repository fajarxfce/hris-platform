package dev.fajar.hris.identity.domain.entities

data class AccountAccess(
    val account: Account,
    val permissions: Set<String>,
    val membershipActive: Boolean,
    val companyActive: Boolean,
    val securityPermissions: Set<String> = permissions,
)

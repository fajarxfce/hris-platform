package dev.fajar.hris.identity.domain.entities

data class MembershipGrant(
    val directPermissions: Set<String>,
    val roleTemplates: List<AppliedRoleTemplate>,
)

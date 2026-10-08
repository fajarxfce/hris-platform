package dev.fajar.hris.identity.delivery.responses

data class MemberGrantResponse(
    val member: MemberResponse,
    val directPermissions: Set<String>,
    val roleTemplates: List<AppliedRoleTemplateResponse>,
)

package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

fun CompanyRoleTemplatesRecord.toRoleTemplate() =
    CompanyRoleTemplate(id, companyId, code, name, permissions.toSet(), active, version)

fun CompanyRoleTemplate.toRoleRow() =
    CompanyRoleTemplatesRecord().also {
        it.id = id
        it.companyId = companyId
        it.code = code
        it.name = name
        it.permissions = permissions.sorted().toTypedArray()
        it.active = active
        it.version = version
    }

fun MembershipGrant.toSnapshot(json: ObjectMapper): String = json.writeValueAsString(this)

fun membershipGrantFromSnapshot(value: String, json: ObjectMapper): MembershipGrant {
    val root = json.readTree(value)
    val direct = root.get("directPermissions")
    val templates = root.get("roleTemplates")
    require(direct.isArray && direct.size() <= 200 && templates.isArray && templates.size() <= 8)
    return MembershipGrant(
        (0 until direct.size()).map { direct.get(it).asString() }.toSet(),
        (0 until templates.size()).map { index ->
            val role = templates.get(index)
            val permissions = role.get("permissions")
            require(permissions.isArray && permissions.size() <= 200)
            AppliedRoleTemplate(
                UUID.fromString(role.get("id").asString()),
                role.get("code").asString(),
                role.get("name").asString(),
                role.get("version").asLong(),
                (0 until permissions.size()).map { permissions.get(it).asString() }.toSet(),
            )
        },
    )
}

package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.usecases.SaveCompanyMembership
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class MembershipCommandApiFixture : RoleTemplateApiFixture() {
    protected fun member(f: RoleFixture, applyRole: Boolean = true): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Member review fixture',password_hash from accounts where id=?",
                id,
                "$id@example.test",
                f.operator,
            )
        body(
            assign(
                f,
                id,
                roles = if (applyRole) listOf(RoleTemplateSelection(f.id, 0)) else emptyList(),
            )
        )
        return id
    }

    protected fun assign(
        f: RoleFixture,
        target: UUID,
        version: Long? = null,
        active: Boolean = true,
        permissions: Set<String> = emptySet(),
        roles: List<RoleTemplateSelection> = emptyList(),
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "/api/v1/companies/${f.company}/members/$target",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "active" to active,
                    "permissions" to permissions,
                    "roleTemplates" to
                        roles.map {
                            mapOf(
                                "id" to it.id,
                                "version" to it.version,
                                "retainApplied" to it.retainApplied,
                            )
                        },
                    "reason" to "Reviewed company access",
                )
            ),
            f.csrf,
            key,
            "PUT",
        )

    protected fun memberSave(
        changes: ChangeJournalRepository = journal,
        security: IdentitySecurityPolicy = IdentitySecurityPolicy(enforceMfa = false),
    ) =
        SaveCompanyMembership(
            members,
            identities,
            operations,
            changes,
            transactions,
            clock,
            security,
            roles,
        )

    protected fun grant(f: RoleFixture, target: UUID) =
        body(get(f.browser, "/api/v1/companies/${f.company}/members/$target"))

    protected fun assertMemberUnchanged(f: RoleFixture, target: UUID, key: UUID) {
        val current = grant(f, target)
        assertEquals(0L, current["member"]["version"].asLong())
        assertEquals(
            listOf("company.read"),
            current["member"]["permissions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(0L, current["roleTemplates"][0]["version"].asLong())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from membership_role_applications where company_id=? and account_id=?",
                    Int::class.java,
                    f.company,
                    target,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    key,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and resource_id=? and action='identity.membership_saved'",
                    Int::class.java,
                    f.company,
                    target,
                ),
        )
    }
}

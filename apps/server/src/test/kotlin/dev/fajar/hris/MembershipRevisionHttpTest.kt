package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.RoleTemplateSelection
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MembershipRevisionHttpTest : MembershipCommandApiFixture() {
    @Test
    fun legacyRoleSelectionsAndExplicitFalseRecoverTheSameReceipt() {
        val f = roleFixture()
        val target = member(f)
        val key = UUID.randomUUID()
        val legacy =
            command(
                f.browser,
                "/api/v1/companies/${f.company}/members/$target",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "active" to true,
                        "permissions" to emptyList<String>(),
                        "roleTemplates" to listOf(mapOf("id" to f.id, "version" to 0)),
                        "reason" to "Reviewed company access",
                    )
                ),
                f.csrf,
                key,
                "PUT",
            )
        body(legacy)
        val replay = assign(f, target, 0, roles = listOf(RoleTemplateSelection(f.id, 0)), key = key)
        assertEquals(json.readTree(legacy.body()), body(replay))
        assertEquals(1L, grant(f, target)["member"]["version"].asLong())
    }

    @Test
    fun companyAccessCanBeRemovedFromAGloballyDisabledAccountButNotReactivated() {
        val f = roleFixture()
        val target = member(f)
        database().update("update accounts set active=false where id=?", target)
        body(
            assign(
                f,
                target,
                0,
                active = false,
                roles = listOf(RoleTemplateSelection(f.id, 0, true)),
            )
        )
        val disabled = grant(f, target)
        assertFalse(disabled["member"]["active"].asBoolean())
        assertFalse(disabled["member"]["accountActive"].asBoolean())
        val rejected = assign(f, target, 1, roles = listOf(RoleTemplateSelection(f.id, 0)))
        assertEquals(422, rejected.statusCode(), rejected.body())
        assertEquals("account_unavailable", json.readTree(rejected.body())["code"].asString())
        assertEquals(disabled, grant(f, target))
    }

    @Test
    fun keepingAnAppliedRoleRetainsItsEvidenceAcrossDefinitionChangesAndArchival() {
        val f = roleFixture()
        val target = member(f)
        val changed =
            saving()
                .execute(
                    actor(f),
                    UUID.randomUUID(),
                    f.id,
                    0,
                    "REVIEWER",
                    "New reviewer",
                    setOf("people.read"),
                    true,
                    "New definition",
                )
        assertTrue(changed is Result.Success, changed.toString())
        val before = grant(f, target)
        val stale = assign(f, target, 0, roles = listOf(RoleTemplateSelection(f.id, 0)))
        assertEquals(409, stale.statusCode(), stale.body())
        val key = UUID.randomUUID()
        val retained =
            assign(
                f,
                target,
                0,
                permissions = setOf("people.team.read"),
                roles = listOf(RoleTemplateSelection(f.id, 0, true)),
                key = key,
            )
        body(retained)
        val after = grant(f, target)
        assertEquals(before["roleTemplates"], after["roleTemplates"])
        assertEquals(
            setOf("company.read", "people.team.read"),
            after["member"]["permissions"].iterator().asSequence().map { it.asString() }.toSet(),
        )
        body(save(f, 1, active = false))
        val replay =
            assign(
                f,
                target,
                0,
                permissions = setOf("people.team.read"),
                roles = listOf(RoleTemplateSelection(f.id, 0, true)),
                key = key,
            )
        assertEquals(json.readTree(retained.body()), body(replay))
        body(
            assign(
                f,
                target,
                1,
                active = false,
                roles = listOf(RoleTemplateSelection(f.id, 0, true)),
            )
        )
        val disabled = grant(f, target)
        assertFalse(disabled["member"]["active"].asBoolean())
        assertEquals(before["roleTemplates"], disabled["roleTemplates"])
        val rejected = assign(f, target, 2, roles = listOf(RoleTemplateSelection(f.id, 0, true)))
        assertEquals(422, rejected.statusCode(), rejected.body())
        assertEquals(
            "role_template_review_required",
            json.readTree(rejected.body())["code"].asString(),
        )
        val archived = assign(f, target, 2, roles = listOf(RoleTemplateSelection(f.id, 2)))
        assertEquals(422, archived.statusCode(), archived.body())
        body(save(f, 2, active = true))
        body(assign(f, target, 2, roles = listOf(RoleTemplateSelection(f.id, 3))))
        assertEquals(3L, grant(f, target)["roleTemplates"][0]["version"].asLong())
    }

    @Test
    fun retainedSelectionsCannotBorrowAnotherMemberCompanyOrUnappliedRevision() {
        val f = roleFixture()
        val first = member(f)
        val withoutRole = member(f, applyRole = false)
        val other = roleFixture()
        val foreign = member(other, applyRole = false)
        for ((company, target, selected) in
            listOf(
                Triple(f, first, RoleTemplateSelection(f.id, 1, true)),
                Triple(f, first, RoleTemplateSelection(UUID.randomUUID(), 0, true)),
                Triple(f, withoutRole, RoleTemplateSelection(f.id, 0, true)),
                Triple(other, foreign, RoleTemplateSelection(f.id, 0, true)),
            )) {
            val rejected = assign(company, target, 0, roles = listOf(selected))
            assertEquals(409, rejected.statusCode(), rejected.body())
            assertEquals("stale_role_template", json.readTree(rejected.body())["code"].asString())
            assertEquals(0L, grant(company, target)["member"]["version"].asLong())
        }
        val missing =
            assign(f, UUID.randomUUID(), roles = listOf(RoleTemplateSelection(f.id, 0, true)))
        assertEquals(422, missing.statusCode(), missing.body())
        val stale = assign(f, first, 1, roles = listOf(RoleTemplateSelection(f.id, 0, true)))
        assertEquals(409, stale.statusCode(), stale.body())
        assertEquals("stale_version", json.readTree(stale.body())["code"].asString())
    }

    @Test
    fun retainingAndApplyingAreDifferentCommandsAndCannotShareAReceipt() {
        val f = roleFixture()
        val target = member(f)
        val key = UUID.randomUUID()
        val retained =
            assign(f, target, 0, roles = listOf(RoleTemplateSelection(f.id, 0, true)), key = key)
        body(retained)
        val changedIntent =
            assign(f, target, 0, roles = listOf(RoleTemplateSelection(f.id, 0)), key = key)
        assertEquals(409, changedIntent.statusCode(), changedIntent.body())
        val mixed =
            assign(
                f,
                target,
                1,
                roles = listOf(RoleTemplateSelection(f.id, 0, true), RoleTemplateSelection(f.id, 0)),
            )
        assertEquals(422, mixed.statusCode(), mixed.body())
        val replay =
            assign(f, target, 0, roles = listOf(RoleTemplateSelection(f.id, 0, true)), key = key)
        assertEquals(json.readTree(retained.body()), body(replay))
    }

    @Test
    fun retainedSensitiveRolesCannotReactivateWithoutFreshReviewAndPlatformAuthority() {
        val f = roleFixture()
        val target = member(f)
        val role =
            command(
                f.browser,
                "${f.path}/${f.id}",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "code" to "REVIEWER",
                        "name" to "Payroll reviewer",
                        "permissions" to setOf("payroll.read"),
                        "reason" to "Reviewed restricted role",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        body(role)
        body(assign(f, target, 0, roles = listOf(RoleTemplateSelection(f.id, 1))))
        body(
            assign(
                f,
                target,
                1,
                active = false,
                roles = listOf(RoleTemplateSelection(f.id, 1, true)),
            )
        )
        val operator = actor(f)
        val retained =
            memberSave()
                .execute(
                    operator,
                    UUID.randomUUID(),
                    target,
                    2,
                    true,
                    emptySet(),
                    "Reactivate reviewed access",
                    listOf(RoleTemplateSelection(f.id, 1, true)),
                )
        assertEquals("role_template_review_required", (retained as? Result.Failed)?.failure?.code)
        val applied =
            memberSave()
                .execute(
                    operator,
                    UUID.randomUUID(),
                    target,
                    2,
                    true,
                    emptySet(),
                    "Reactivate reviewed access",
                    listOf(RoleTemplateSelection(f.id, 1)),
                )
        assertEquals("platform_administrator_required", (applied as? Result.Failed)?.failure?.code)
        assertFalse(grant(f, target)["member"]["active"].asBoolean())
    }
}

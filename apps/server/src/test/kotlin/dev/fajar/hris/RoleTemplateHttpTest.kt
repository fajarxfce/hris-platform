package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class RoleTemplateHttpTest : ApiIntegrationTest() {
    private data class Fixture(val browser: HttpClient, val csrf: String, val company: UUID) {
        val base
            get() = "/api/v1/companies/$company"
    }

    private fun fixture(): Fixture {
        val browser = client()
        val csrf = login(browser)
        val created =
            command(
                browser,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "R${UUID.randomUUID().toString().take(8)}",
                        "name" to "Role Test",
                        "timezone" to "Asia/Jakarta",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        return Fixture(
            browser,
            csrf,
            UUID.fromString(json.readTree(created.body()).get("id").asString()),
        )
    }

    private fun account(): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Role employee',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        return id
    }

    private fun role(
        f: Fixture,
        id: UUID,
        permissions: Set<String>,
        version: Long? = null,
        active: Boolean = true,
        code: String = "ROLE${id.toString().take(8)}",
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            f.browser,
            "${f.base}/role-templates/$id",
            json.writeValueAsString(
                mapOf(
                    "code" to code,
                    "name" to "Operations",
                    "permissions" to permissions,
                    "expectedVersion" to version,
                    "active" to active,
                    "reason" to "Reviewed role definition",
                )
            ),
            f.csrf,
            key,
            "PUT",
        )

    private fun apply(
        f: Fixture,
        account: UUID,
        permissions: Set<String> = setOf("company.read"),
        templates: List<Pair<UUID, Long>> = emptyList(),
        version: Long? = null,
        key: UUID = UUID.randomUUID(),
        active: Boolean = true,
    ): HttpResponse<String> =
        command(
            f.browser,
            "${f.base}/members/$account",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "active" to active,
                    "permissions" to permissions,
                    "roleTemplates" to
                        templates.map { mapOf("id" to it.first, "version" to it.second) },
                    "reason" to "Approved access assignment",
                )
            ),
            f.csrf,
            key,
            "PUT",
        )

    @Test
    fun assignmentsFreezeTemplateVersionsAndRequireExplicitReapplication() {
        val f = fixture()
        val target = account()
        val id = UUID.randomUUID()
        assertEquals(200, role(f, id, setOf("people.team.read")).statusCode())
        assertEquals(200, apply(f, target, templates = listOf(id to 0)).statusCode())
        val initial = json.readTree(get(f.browser, "${f.base}/members/$target").body())
        assertEquals(1, initial.get("roleTemplates").size())
        assertEquals(0, initial.get("roleTemplates").get(0).get("version").asLong())
        assertEquals("company.read", initial.get("directPermissions").get(0).asString())
        assertEquals(200, role(f, id, setOf("people.read"), 0).statusCode())
        val unchanged = json.readTree(get(f.browser, "${f.base}/members/$target").body())
        assertEquals(initial, unchanged)
        assertEquals(409, apply(f, target, templates = listOf(id to 0), version = 0).statusCode())
        val key = UUID.randomUUID()
        val applied = apply(f, target, templates = listOf(id to 1), version = 0, key = key)
        assertEquals(200, applied.statusCode(), applied.body())
        val updated = json.readTree(get(f.browser, "${f.base}/members/$target").body())
        val permissions = updated.get("member").get("permissions")
        assertTrue(
            (0 until permissions.size()).any { permissions.get(it).asString() == "people.read" }
        )
        assertFalse(
            (0 until permissions.size()).any {
                permissions.get(it).asString() == "people.team.read"
            }
        )
        assertEquals(200, role(f, id, setOf("people.read"), 1, false).statusCode())
        assertEquals(
            applied.body(),
            apply(f, target, templates = listOf(id to 1), version = 0, key = key).body(),
        )
        assertEquals(422, apply(f, account(), templates = listOf(id to 2)).statusCode())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from membership_role_applications where company_id=? and account_id=?",
                    Int::class.java,
                    f.company,
                    target,
                ),
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update("delete from membership_role_applications where company_id=?", f.company)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update role_template_revisions set name='Changed' where company_id=?",
                    f.company,
                )
        }
    }

    @Test
    fun templatesCannotBypassSensitiveGrantRulesOrCompanyScope() {
        val f = fixture()
        val catalog = get(f.browser, "${f.base}/role-templates/permissions")
        assertEquals(200, catalog.statusCode(), catalog.body())
        assertTrue(catalog.body().contains("payroll.finalize"))
        assertFalse(catalog.body().contains("companies.create"))
        val localAdmin = account()
        val target = account()
        val id = UUID.randomUUID()
        assertEquals(
            200,
            apply(f, localAdmin, setOf("company.read", "identity.manage")).statusCode(),
        )
        val localBrowser = client()
        val csrf = login(localBrowser, "$localAdmin@example.test")
        val local = Fixture(localBrowser, csrf, f.company)
        assertEquals(
            200,
            role(local, id, setOf("payroll.finalize", "identity.manage")).statusCode(),
        )
        assertEquals(403, apply(local, target, templates = listOf(id to 0)).statusCode())
        assertEquals(
            403,
            apply(local, localAdmin, templates = listOf(id to 0), version = 0).statusCode(),
        )
        assertEquals(200, apply(f, target, templates = listOf(id to 0)).statusCode())
        assertEquals(422, apply(f, account(), templates = listOf(id to 0, id to 0)).statusCode())
        assertEquals(
            200,
            apply(f, target, templates = listOf(id to 0), version = 0, active = false).statusCode(),
        )
        assertEquals(
            403,
            apply(local, target, templates = listOf(id to 0), version = 1).statusCode(),
        )
        val other = fixture()
        assertEquals(422, apply(other, account(), templates = listOf(id to 0)).statusCode())
        assertEquals(
            0,
            json
                .readTree(get(other.browser, "${other.base}/role-templates").body())
                .get("items")
                .size(),
        )
        assertEquals(404, get(other.browser, "${other.base}/members/$target").statusCode())
        assertEquals(422, role(f, UUID.randomUUID(), setOf("unregistered.permission")).statusCode())
        assertEquals(422, get(f.browser, "${f.base}/role-templates?limit=201").statusCode())
    }

    @Test
    fun conflictingRoleCodesAndVersionsCannotCreateDuplicateHistory() {
        val f = fixture()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        Executors.newFixedThreadPool(2).use { executor ->
            val requests =
                ids.map { id ->
                    executor.submit<Int> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        role(f, id, setOf("company.read"), code = "OPERATIONS").statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(listOf(200, 409), requests.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        val id =
            database()
                .queryForObject(
                    "select id from company_role_templates where company_id=?",
                    UUID::class.java,
                    f.company,
                )!!
        val key = UUID.randomUUID()
        val result = role(f, id, setOf("people.read"), 0, code = "OPERATIONS", key = key)
        assertEquals(200, result.statusCode(), result.body())
        assertEquals(
            result.body(),
            role(f, id, setOf("people.read"), 0, code = "OPERATIONS", key = key).body(),
        )
        assertEquals(
            409,
            role(f, id, setOf("people.team.read"), 0, code = "OPERATIONS").statusCode(),
        )
        assertEquals(409, role(f, id, setOf("people.read"), 1, code = "NEW_CODE").statusCode())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from role_template_revisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun failedAssignmentAuditRollsBackGrantsSourcesAndOperationReceipt() {
        val f = fixture()
        val target = account()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        assertEquals(200, role(f, id, setOf("people.read")).statusCode())
        database()
            .execute(
                """create function fail_role_assignment_audit() returns trigger language plpgsql as ${'$'}${'$'}
            begin if new.resource_id='${target}'::uuid and new.action='identity.membership_saved' then
                raise exception 'Fixture failure' using errcode='23514'; end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger role_assignment_probe before insert on audit_entries for each row execute function fail_role_assignment_audit()"
            )
        try {
            assertEquals(409, apply(f, target, templates = listOf(id to 0), key = key).statusCode())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from company_memberships where company_id=? and account_id=?",
                        Int::class.java,
                        f.company,
                        target,
                    ),
            )
            assertEquals(
                0,
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
        } finally {
            database().execute("drop trigger role_assignment_probe on audit_entries")
            database().execute("drop function fail_role_assignment_audit()")
        }
        assertEquals(200, apply(f, target, templates = listOf(id to 0), key = key).statusCode())
    }

    @Test
    fun templateCapacityIsBoundedAndNewDefinitionsStillDoNotGrantPermissions() {
        val f = fixture()
        database()
            .update(
                "insert into company_role_templates(company_id,id,code,name,permissions) select ?,gen_random_uuid(),'R'||i,'Reserved',array['company.read'] from generate_series(1,128) i",
                f.company,
            )
        assertEquals(409, role(f, UUID.randomUUID(), setOf("company.read")).statusCode())
        val page = json.readTree(get(f.browser, "${f.base}/role-templates?limit=10").body())
        assertEquals(10, page.get("items").size())
        assertTrue(page.get("nextCursor").isString)
    }
}

package dev.fajar.hris

import java.net.http.HttpClient
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class AccountAdministrationHttpTest : ApiIntegrationTest() {
    private fun account(admin: Boolean = false, pending: Boolean = false): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,active,invitation_pending) select ?,?,'Managed employee',password_hash,?,? from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
                !pending,
                pending,
            )
        if (admin)
            database()
                .update(
                    "insert into platform_permissions(account_id,permission) values(?,'identity.manage'),(?,'companies.create')",
                    id,
                    id,
                )
        return id
    }

    private fun save(
        browser: HttpClient,
        csrf: String,
        id: UUID,
        version: Long = 0,
        active: Boolean = false,
        permissions: Set<String> = emptySet(),
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            browser,
            "/api/v1/identity/accounts/$id/access",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "active" to active,
                    "platformPermissions" to permissions,
                    "reason" to "Reviewed account access",
                )
            ),
            csrf,
            key,
            "PUT",
        )

    @Test
    fun globalDirectoriesHideCredentialsAndDenyCompanyOnlyAdministrators() {
        val admin = client()
        val csrf = login(admin)
        val target = account()
        val result = get(admin, "/api/v1/identity/accounts?query=$target&limit=1")
        assertEquals(200, result.statusCode(), result.body())
        val page = json.readTree(result.body())
        assertEquals(target.toString(), page.get("items").get(0).get("id").asString())
        assertFalse(result.body().contains("password"))
        assertFalse(result.body().contains("secret"))
        val local = client()
        login(local, "$target@example.test")
        assertEquals(403, get(local, "/api/v1/identity/accounts").statusCode())
        val localCsrf =
            json.readTree(get(local, "/api/v1/auth/csrf").body()).get("token").asString()
        assertEquals(403, save(local, localCsrf, account()).statusCode())
        assertEquals(422, get(admin, "/api/v1/identity/accounts?limit=201").statusCode())
        assertEquals(
            422,
            save(admin, csrf, target, permissions = setOf("payroll.finalize")).statusCode(),
        )
    }

    @Test
    fun accessChangesRevokeSessionsAndPreserveTheirIdempotencyReceipts() {
        val admin = client()
        val csrf = login(admin)
        val target = account()
        val user = client()
        login(user, "$target@example.test")
        val key = UUID.randomUUID()
        val disabled = save(admin, csrf, target, key = key)
        assertEquals(200, disabled.statusCode(), disabled.body())
        assertEquals(401, get(user, "/api/v1/me").statusCode())
        assertEquals(disabled.body(), save(admin, csrf, target, key = key).body())
        assertEquals(409, save(admin, csrf, target, active = true, key = key).statusCode())
        assertEquals(200, save(admin, csrf, target, version = 1, active = true).statusCode())
        assertEquals(401, get(user, "/api/v1/me").statusCode())
        login(user, "$target@example.test")
        assertEquals(200, get(user, "/api/v1/me").statusCode())
        assertEquals(
            200,
            save(
                    admin,
                    csrf,
                    target,
                    version = 2,
                    active = true,
                    permissions = setOf("identity.manage"),
                )
                .statusCode(),
        )
        assertEquals(401, get(user, "/api/v1/me").statusCode())
        login(user, "$target@example.test")
        assertEquals(200, get(user, "/api/v1/identity/accounts").statusCode())
        assertEquals(
            3,
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Int::class.java,
                    target,
                ),
        )
        assertThrows(DataAccessException::class.java) {
            database().update("update accounts set email='changed@example.test' where id=?", target)
        }
    }

    @Test
    fun competingAdministratorsCannotDisableEachOther() {
        val a = account(admin = true)
        val b = account(admin = true)
        val browserA = client()
        val csrfA = login(browserA, "$a@example.test")
        val browserB = client()
        val csrfB = login(browserB, "$b@example.test")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val first =
                executor.submit<Int> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    save(browserA, csrfA, b).statusCode()
                }
            val second =
                executor.submit<Int> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    save(browserB, csrfB, a).statusCode()
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(
                listOf(200, 401),
                listOf(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)).sorted(),
            )
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from accounts where id in (?,?) and active",
                    Int::class.java,
                    a,
                    b,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id in (?,?) and action='identity.account_access_saved'",
                    Int::class.java,
                    a,
                    b,
                ),
        )
    }

    @Test
    fun failedAuditDoesNotConsumeTheOperationOrRevokeCredentials() {
        val admin = client()
        val csrf = login(admin)
        val target = account()
        val user = client()
        login(user, "$target@example.test")
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_account_access_audit() returns trigger language plpgsql as ${'$'}${'$'}
            begin if new.resource_id='$target'::uuid and new.action='identity.account_access_saved' then
                raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger account_access_probe before insert on audit_entries for each row execute function fail_account_access_audit()"
            )
        try {
            assertEquals(409, save(admin, csrf, target, key = key).statusCode())
            assertEquals(200, get(user, "/api/v1/me").statusCode())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select security_version from accounts where id=?",
                        Int::class.java,
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
            database().execute("drop trigger account_access_probe on audit_entries")
            database().execute("drop function fail_account_access_audit()")
        }
        assertEquals(200, save(admin, csrf, target, key = key).statusCode())
    }

    @Test
    fun administratorsCannotRemoveTheirOwnAccessOrBypassPendingAcceptance() {
        val admin = client()
        val csrf = login(admin)
        val me =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        assertEquals(409, save(admin, csrf, me).statusCode())
        assertEquals(409, save(admin, csrf, me, active = true).statusCode())
        val pending = account(pending = true)
        assertEquals(409, save(admin, csrf, pending, active = true).statusCode())
        assertEquals(200, save(admin, csrf, pending).statusCode())
        assertEquals(
            false,
            database()
                .queryForObject(
                    "select invitation_pending from accounts where id=?",
                    Boolean::class.java,
                    pending,
                ),
        )
        assertEquals(404, save(admin, csrf, UUID.randomUUID()).statusCode())
    }
}

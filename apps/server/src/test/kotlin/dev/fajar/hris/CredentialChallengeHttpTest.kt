package dev.fajar.hris

import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class CredentialChallengeHttpTest : CredentialApiFixture() {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var credentials:
        dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var deliveries:
        dev.fajar.hris.identity.domain.repositories.IdentityMailRepository
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var journal: dev.fajar.hris.core.domain.ChangeJournalRepository
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var transactions: dev.fajar.hris.core.domain.TransactionRunner
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var limits:
        dev.fajar.hris.identity.domain.repositories.AuthenticationRateLimitRepository

    @Test
    fun expiryDuringPasswordHashingRollsBackThePasswordAndPreservesTheUnconsumedLink() {
        val admin = client()
        val csrf = login(admin)
        val id = UUID.randomUUID()
        assertEquals(200, invite(admin, csrf, id).statusCode())
        val (challenge, token) = link(id)
        val slow =
            object :
                dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository by credentials {
                override fun setPassword(
                    id: UUID,
                    expectedVersion: Long,
                    password: String,
                    active: Boolean,
                ): dev.fajar.hris.core.domain.Result<dev.fajar.hris.core.domain.MutationReceipt> {
                    val result = credentials.setPassword(id, expectedVersion, password, active)
                    clock.set(clock.instant().plusSeconds(4 * 86400))
                    return result
                }
            }
        val confirm =
            dev.fajar.hris.identity.domain.usecases.ConfirmAccountCredential(
                slow,
                deliveries,
                journal,
                transactions,
                clock,
            )
        assertTrue(
            confirm.execute(
                dev.fajar.hris.identity.domain.entities.CredentialChallengeKind.INVITATION,
                token,
                "Replacement-password-123!",
                UUID.randomUUID(),
            ) is dev.fajar.hris.core.domain.Result.Failed
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from accounts where id=? and password_hash is not null",
                    Int::class.java,
                    id,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from identity_challenges where id=? and consumed_at is not null",
                    Int::class.java,
                    challenge,
                ),
        )
        assertEquals(token, link(id).second)
    }

    @Test
    fun invitationIsAtomicIdempotentPrivateAndAcceptedOnlyOnceUnderCompetition() {
        val admin = client()
        val adminCsrf = login(admin)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val invited = invite(admin, adminCsrf, id, key)
        assertEquals(200, invited.statusCode(), invited.body())
        assertEquals(invited.body(), invite(admin, adminCsrf, id, key).body())
        val (challenge, token) = link(id)
        assertFalse(invited.body().contains(token))
        assertFalse(invited.body().contains("token"))
        assertNotEquals(
            token,
            database()
                .queryForObject(
                    "select token_hash from identity_challenges where id=?",
                    String::class.java,
                    challenge,
                ),
        )
        assertFalse(
            database()
                .queryForObject(
                    "select token_encrypted from identity_mail_deliveries where challenge_id=?",
                    String::class.java,
                    challenge,
                )!!
                .contains(token)
        )
        val anonymous = client()
        val csrf = csrf(anonymous)
        assertEquals(
            403,
            post(
                    anonymous,
                    "/api/v1/auth/invitations/accept",
                    json.writeValueAsString(
                        mapOf("token" to token, "password" to "Replacement-password-123!")
                    ),
                )
                .statusCode(),
        )
        assertEquals(422, confirm(anonymous, csrf, token, recovery = true).statusCode())
        assertEquals(
            401,
            post(
                    anonymous,
                    "/api/v1/auth/login",
                    json.writeValueAsString(
                        mapOf(
                            "email" to "$id@example.test",
                            "password" to "Replacement-password-123!",
                        )
                    ),
                    csrf,
                )
                .statusCode(),
        )
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        confirm(anonymous, csrf, token).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(listOf(204, 422), results.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(422, confirm(anonymous, csrf, token).statusCode())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='identity.invitation_accepted'",
                    Int::class.java,
                    id,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from identity_mail_deliveries where account_id=? and token_encrypted is not null",
                    Int::class.java,
                    id,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from company_memberships where account_id=?",
                    Int::class.java,
                    id,
                ),
        )
        val signed =
            post(
                anonymous,
                "/api/v1/auth/login",
                json.writeValueAsString(
                    mapOf("email" to "$id@example.test", "password" to "Replacement-password-123!")
                ),
                csrf,
            )
        assertEquals(200, signed.statusCode(), signed.body())
        assertThrows(DataAccessException::class.java) {
            database()
                .update("update identity_challenges set consumed_at=null where id=?", challenge)
        }
    }

    @Test
    fun explicitResendExpiryAndAdministrativeRevocationInvalidateOlderLinks() {
        val admin = client()
        var adminCsrf = login(admin)
        val id = UUID.randomUUID()
        assertEquals(200, invite(admin, adminCsrf, id).statusCode())
        val old = link(id).second
        assertEquals(409, invite(admin, adminCsrf, id).statusCode())
        assertEquals(200, invite(admin, adminCsrf, id, version = 0).statusCode())
        val current = link(id).second
        val browser = client()
        val csrf = csrf(browser)
        assertEquals(422, confirm(browser, csrf, old).statusCode())
        clock.set(clock.instant().plus(Duration.ofDays(4)))
        assertEquals(422, confirm(browser, csrf, current).statusCode())
        adminCsrf = login(admin)
        assertEquals(200, invite(admin, adminCsrf, id, version = 1).statusCode())
        val renewed = link(id).second
        val disabled =
            command(
                admin,
                "/api/v1/identity/accounts/$id/access",
                """{"expectedVersion":2,"active":false,"platformPermissions":[],"reason":"Cancelled invitation"}""",
                adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, disabled.statusCode(), disabled.body())
        assertEquals(422, confirm(browser, csrf, renewed).statusCode())
        assertEquals(409, invite(admin, adminCsrf, localAccount(), version = 0).statusCode())
    }

    @Test
    fun failedAuditRollsBackAccountChallengeCiphertextAndPasswordConsumption() {
        val admin = client()
        val adminCsrf = login(admin)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_invitation_audit() returns trigger language plpgsql as ${'$'}${'$'} begin
            if new.resource_id='$id'::uuid and new.action in ('identity.account_invited','identity.invitation_accepted') then
            raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger invitation_probe before insert on audit_entries for each row execute function fail_invitation_audit()"
            )
        try {
            assertEquals(409, invite(admin, adminCsrf, id, key).statusCode())
            assertEquals(
                0,
                database()
                    .queryForObject("select count(*) from accounts where id=?", Int::class.java, id),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from identity_challenges where account_id=?",
                        Int::class.java,
                        id,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from identity_mail_deliveries where account_id=?",
                        Int::class.java,
                        id,
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
            database().execute("alter table audit_entries disable trigger invitation_probe")
            assertEquals(200, invite(admin, adminCsrf, id, key).statusCode())
            database().execute("alter table audit_entries enable trigger invitation_probe")
            val (challenge, token) = link(id)
            val browser = client()
            val csrf = csrf(browser)
            assertEquals(409, confirm(browser, csrf, token).statusCode())
            assertEquals(
                false,
                database()
                    .queryForObject(
                        "select active from accounts where id=?",
                        Boolean::class.java,
                        id,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from accounts where id=? and password_hash is not null",
                        Int::class.java,
                        id,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from identity_challenges where id=? and consumed_at is not null",
                        Int::class.java,
                        challenge,
                    ),
            )
            assertEquals(token, link(id).second)
            database().execute("alter table audit_entries disable trigger invitation_probe")
            assertEquals(204, confirm(browser, csrf, token).statusCode())
        } finally {
            database().execute("drop trigger invitation_probe on audit_entries")
            database().execute("drop function fail_invitation_audit()")
        }
    }

    @Test
    fun recoveryResponsesHideEligibilityAndUseAnIndependentBoundedBudget() {
        val browser = client()
        val csrf = csrf(browser)
        val account = localAccount()
        val inactive = localAccount(active = false)
        val oidc = localAccount(password = false)
        for (id in listOf(account, inactive, oidc, UUID.randomUUID())) {
            repeat(4) {
                val result = recover(browser, csrf, "$id@example.test")
                assertEquals(202, result.statusCode(), result.body())
                assertTrue(result.body().isEmpty())
            }
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from identity_challenges where account_id in (?,?,?)",
                    Int::class.java,
                    account,
                    inactive,
                    oidc,
                ),
        )
        val rateActor =
            dev.fajar.hris.core.domain.Actor(
                UUID(0, 0),
                null,
                emptySet(),
                clock.instant(),
                UUID.randomUUID(),
            )
        assertEquals(
            dev.fajar.hris.core.domain.Result.Success(true),
            transactions.run(rateActor) {
                limits.takeAttempt(
                    "$account@example.test",
                    "127.0.0.1",
                    clock.instant(),
                    dev.fajar.hris.identity.domain.entities.AuthenticationAttemptPolicy(
                        perAccount = 1
                    ),
                    dev.fajar.hris.identity.domain.entities.AuthenticationAttemptKind.SIGN_IN,
                )
            },
        )
        val first = link(account).second
        assertEquals(202, recover(browser, csrf, "$account@example.test").statusCode())
        assertEquals(first, link(account).second)
        val user = client()
        login(user, "$account@example.test")
        assertEquals(200, get(user, "/api/v1/me").statusCode())
        val outsider = client()
        val outsiderCsrf = login(outsider, "$account@example.test")
        assertEquals(403, invite(outsider, outsiderCsrf, UUID.randomUUID()).statusCode())
        assertEquals(
            403,
            post(browser, "/api/v1/auth/password-recovery", """{"email":"missing@example.test"}""")
                .statusCode(),
        )
        assertEquals(
            422,
            confirm(browser, csrf, first, recovery = true, password = "short").statusCode(),
        )
        assertEquals(204, confirm(browser, csrf, first, recovery = true).statusCode())
        assertEquals(401, get(user, "/api/v1/me").statusCode())
    }

    @Test
    fun passwordRecoveryKeepsMfaAndRevokesCookieAndNativeCredentials() {
        val account = localAccount()
        val user = client()
        val userCsrf = login(user, "$account@example.test")
        val exchange =
            command(
                user,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Credential test"}""",
                userCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchange.statusCode(), exchange.body())
        val access = json.readTree(exchange.body()).get("accessToken").asString()
        database()
            .update(
                "update accounts set mfa_secret_encrypted='retained-fixture-secret',mfa_last_counter=123 where id=?",
                account,
            )
        val browser = client()
        val csrf = csrf(browser)
        assertEquals(202, recover(browser, csrf, "$account@example.test").statusCode())
        val token = link(account).second
        assertEquals(204, confirm(browser, csrf, token, recovery = true).statusCode())
        assertEquals(
            "retained-fixture-secret",
            database()
                .queryForObject(
                    "select mfa_secret_encrypted from accounts where id=?",
                    String::class.java,
                    account,
                ),
        )
        assertEquals(
            123,
            database()
                .queryForObject(
                    "select mfa_last_counter from accounts where id=?",
                    Int::class.java,
                    account,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Int::class.java,
                    account,
                ),
        )
        assertEquals(401, get(user, "/api/v1/me").statusCode())
        val native =
            browser.send(
                java.net.http.HttpRequest.newBuilder(
                        java.net.URI("http://127.0.0.1:$port/api/v1/me")
                    )
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer $access")
                    .GET()
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(401, native.statusCode(), native.body())
        assertEquals(422, confirm(browser, csrf, token, recovery = true).statusCode())
    }

    @Test
    fun recoveryLinksAreRejectedAfterDisablingTheAccountOrChangingItsSecurityVersion() {
        val browser = client()
        val csrf = csrf(browser)
        for (disable in listOf(true, false)) {
            val account = localAccount()
            assertEquals(202, recover(browser, csrf, "$account@example.test").statusCode())
            val token = link(account).second
            database()
                .update(
                    "update accounts set active=?,security_version=security_version+1 where id=?",
                    !disable,
                    account,
                )
            assertEquals(422, confirm(browser, csrf, token, recovery = true).statusCode())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from identity_challenges where account_id=? and consumed_at is not null",
                        Int::class.java,
                        account,
                    ),
            )
        }
    }
}

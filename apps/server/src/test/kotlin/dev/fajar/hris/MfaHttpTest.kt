package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MfaHttpTest : MfaApiFixture() {
    @Test
    fun enrollmentGatesPrivilegedAccessAndRotatesTheSessionWithoutStoringPlainSecrets() {
        val f = fixture()
        val previous = anotherLogin(f)
        assertEquals(403, get(f.client, "/api/v1/test/assurance").statusCode())
        val limited = json.readTree(get(f.client, "/api/v1/me").body())
        assertTrue(limited.get("assurance").get("required").asBoolean())
        assertFalse(limited.get("assurance").get("verified").asBoolean())
        assertEquals(0, limited.get("permissions").size())
        assertEquals(0, limited.get("companies").size())
        val companyBody =
            """{"code":"MFA${UUID.randomUUID().toString().take(6)}","name":"MFA Company","timezone":"UTC"}"""
        val denied = command(f.client, "/api/v1/companies", companyBody, f.csrf, UUID.randomUUID())
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals("mfa_setup_required", json.readTree(denied.body()).get("code").asString())
        val id = UUID.randomUUID()
        val setup = begin(f, id)
        assertEquals(200, setup.statusCode(), setup.body())
        assertEquals(setup.body(), begin(f, id).body())
        assertTrue(setup.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        val secret = json.readTree(setup.body()).get("secret").asString()
        assertTrue(
            json.readTree(setup.body()).get("otpauthUri").asString().contains("HRIS%20Platform")
        )
        val encrypted =
            database()
                .queryForObject(
                    "select secret_encrypted from mfa_enrollments where account_id=?",
                    String::class.java,
                    f.account,
                )!!
        assertFalse(encrypted.contains(secret))
        val before = sessionCookie(f.client)
        val activated = confirm(f, id, totp(secret, clock.instant()))
        assertEquals(200, activated.statusCode(), activated.body())
        assertNotEquals(before, sessionCookie(f.client))
        assertEquals(10, json.readTree(activated.body()).get("recoveryCodes").size())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from mfa_enrollments where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    f.account,
                ),
        )
        assertEquals(401, get(previous.client, "/api/v1/me").statusCode())
        assertEquals(
            403,
            command(f.client, "/api/v1/companies", companyBody, f.csrf, UUID.randomUUID())
                .statusCode(),
        )
        val created =
            command(f.client, "/api/v1/companies", companyBody, csrf(f.client), UUID.randomUUID())
        assertEquals(200, created.statusCode(), created.body())
        val me = json.readTree(get(f.client, "/api/v1/me").body())
        assertTrue(me.get("assurance").get("verified").asBoolean())
        assertEquals(200, get(f.client, "/api/v1/test/assurance").statusCode())
        assertEquals(1, me.get("companies").size())
        val codes = json.readTree(activated.body()).get("recoveryCodes")
        val stored =
            database()
                .queryForList(
                    "select code_hash from mfa_recovery_codes where account_id=?",
                    String::class.java,
                    f.account,
                )
        assertTrue((0 until codes.size()).none { codes[it].asString() in stored })
    }

    @Test
    fun concurrentAuthenticatorAndRecoveryChallengesConsumeEachCodeOnlyOnce() {
        val enrolled = enroll(fixture())
        val f = enrolled.fixture
        val clients = listOf(anotherLogin(f), anotherLogin(f))
        clock.set(clock.instant().plusSeconds(60))
        val code = totp(enrolled.secret, clock.instant())
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val responses =
                clients.map { client ->
                    pool.submit<Int> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        verify(client, code).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(listOf(200, 401), responses.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where actor_id=? and action='identity.mfa_verified'",
                    Int::class.java,
                    f.account,
                ),
        )
        clock.set(clock.instant().plusSeconds(300))
        val recoveryClients = listOf(anotherLogin(f), anotherLogin(f))
        val recoveryReady = CountDownLatch(2)
        val recoveryStart = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val responses =
                recoveryClients.map { client ->
                    pool.submit<Int> {
                        recoveryReady.countDown()
                        check(recoveryStart.await(5, TimeUnit.SECONDS))
                        verify(client, enrolled.codes.first(), true).statusCode()
                    }
                }
            assertTrue(recoveryReady.await(5, TimeUnit.SECONDS))
            recoveryStart.countDown()
            assertEquals(listOf(200, 401), responses.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from mfa_recovery_codes where account_id=? and used_at is not null",
                    Int::class.java,
                    f.account,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where actor_id=? and action='identity.mfa_recovery_used'",
                    Int::class.java,
                    f.account,
                ),
        )
    }

    @Test
    fun recentProofProtectsMembershipAndRegenerationRevokesOldSessionsAndCodes() {
        val enrolled = enroll(fixture())
        val f = enrolled.fixture
        val created =
            command(
                f.client,
                "/api/v1/companies",
                """{"code":"R${UUID.randomUUID().toString().take(8)}","name":"Recent proof","timezone":"UTC"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val company = json.readTree(created.body()).get("id").asString()
        val other = anotherLogin(f)
        clock.set(clock.instant().plusSeconds(601))
        val memberPath = "/api/v1/companies/$company/members/${f.account}"
        val member =
            """{"expectedVersion":0,"active":true,"permissions":["identity.manage","company.read"],"reason":"Security review"}"""
        val denied = command(f.client, memberPath, member, f.csrf, UUID.randomUUID(), "PUT")
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals(
            "recent_authentication_required",
            json.readTree(denied.body()).get("code").asString(),
        )
        assertEquals(
            403,
            post(f.client, "/api/v1/auth/mfa/recovery-codes", "{}", f.csrf).statusCode(),
        )
        val verified = verify(f, totp(enrolled.secret, clock.instant()))
        assertEquals(200, verified.statusCode(), verified.body())
        val fresh = f.copy(csrf = csrf(f.client))
        val saved = command(f.client, memberPath, member, fresh.csrf, UUID.randomUUID(), "PUT")
        assertEquals(200, saved.statusCode(), saved.body())
        val regenerated = post(f.client, "/api/v1/auth/mfa/recovery-codes", "{}", fresh.csrf)
        assertEquals(200, regenerated.statusCode(), regenerated.body())
        assertEquals(401, get(other.client, "/api/v1/me").statusCode())
        val newLogin = anotherLogin(f)
        assertEquals(401, verify(newLogin, enrolled.codes.first(), true).statusCode())
        val newCode = json.readTree(regenerated.body()).get("recoveryCodes")[0].asString()
        val accepted = verify(newLogin, newCode.lowercase().chunked(8).joinToString("-"), true)
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(
            10,
            database()
                .queryForObject(
                    "select count(*) from mfa_recovery_codes where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
    }

    @Test
    fun invalidCodesCommitAttemptCountsAndExpiredEnrollmentsCannotBeConfirmed() {
        val f = fixture()
        val id = UUID.randomUUID()
        val setup = begin(f, id)
        assertEquals(200, setup.statusCode(), setup.body())
        repeat(4) { assertEquals(401, confirm(f, id, "invalid").statusCode()) }
        val limited = confirm(f, id, "invalid")
        assertEquals(429, limited.statusCode(), limited.body())
        assertEquals(
            5,
            database()
                .queryForObject(
                    "select attempts from mfa_attempts where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
        assertNull(
            database()
                .queryForObject(
                    "select mfa_secret_encrypted from accounts where id=?",
                    String::class.java,
                    f.account,
                )
        )
        clock.set(clock.instant().plusSeconds(301))
        val secret = json.readTree(setup.body()).get("secret").asString()
        assertEquals(200, confirm(f, id, totp(secret, clock.instant())).statusCode())
        val expiring = fixture()
        val expiredId = UUID.randomUUID()
        assertEquals(200, begin(expiring, expiredId).statusCode())
        clock.set(clock.instant().plusSeconds(601))
        val relogged = anotherLogin(expiring)
        val expired = confirm(relogged, expiredId, "123456")
        assertEquals(409, expired.statusCode(), expired.body())
        assertEquals("mfa_enrollment_expired", json.readTree(expired.body()).get("code").asString())
    }

    @Test
    fun enrollmentFailureRollsBackCredentialsRecoveryCodesAndCounterConsumption() {
        val f = fixture()
        val id = UUID.randomUUID()
        val setup = begin(f, id)
        assertEquals(200, setup.statusCode(), setup.body())
        val secret = json.readTree(setup.body()).get("secret").asString()
        database()
            .execute(
                """CREATE FUNCTION test_block_mfa_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id='${f.account}'::uuid AND NEW.action='identity.mfa_enrolled' THEN RAISE EXCEPTION 'deliberate audit failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$"""
            )
        database()
            .execute(
                "CREATE TRIGGER test_mfa_audit_failure BEFORE INSERT ON audit_entries FOR EACH ROW EXECUTE FUNCTION test_block_mfa_audit()"
            )
        try {
            val failed = confirm(f, id, totp(secret, clock.instant()))
            assertEquals(409, failed.statusCode(), failed.body())
            assertFalse(failed.body().contains("deliberate"))
            assertEquals(
                0L,
                database()
                    .queryForObject(
                        "select security_version from accounts where id=?",
                        Long::class.java,
                        f.account,
                    ),
            )
            assertNull(
                database()
                    .queryForObject(
                        "select mfa_secret_encrypted from accounts where id=?",
                        String::class.java,
                        f.account,
                    )
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from mfa_recovery_codes where account_id=?",
                        Int::class.java,
                        f.account,
                    ),
            )
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from mfa_enrollments where account_id=?",
                        Int::class.java,
                        f.account,
                    ),
            )
        } finally {
            database().execute("DROP TRIGGER test_mfa_audit_failure ON audit_entries")
            database().execute("DROP FUNCTION test_block_mfa_audit()")
        }
        val retried = confirm(f, id, totp(secret, clock.instant()))
        assertEquals(200, retried.statusCode(), retried.body())
    }
}

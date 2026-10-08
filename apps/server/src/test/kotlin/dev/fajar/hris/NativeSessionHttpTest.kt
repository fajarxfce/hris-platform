package dev.fajar.hris

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeSessionHttpTest : MfaApiFixture() {
    private data class Tokens(val id: UUID, val access: String, val refresh: String) {
        override fun toString(): String = "Tokens(<redacted>)"
    }

    private fun tokens(response: HttpResponse<String>): Tokens {
        assertEquals(200, response.statusCode(), response.body())
        val root = json.readTree(response.body())
        val body = root.get("credentials") ?: root
        return Tokens(
            UUID.fromString(body.get("sessionId").asString()),
            body.get("accessToken").asString(),
            body.get("refreshToken").asString(),
        )
    }

    private fun exchange(
        f: Fixture,
        id: UUID = UUID.randomUUID(),
        name: String = "Test Android",
    ): HttpResponse<String> =
        command(
            f.client,
            "/api/v1/auth/native/exchange",
            json.writeValueAsString(mapOf("deviceName" to name)),
            f.csrf,
            id,
        )

    private fun native(
        client: HttpClient,
        path: String,
        access: String? = null,
        method: String = "GET",
        body: String? = null,
        operationId: UUID? = null,
        rawAuthorization: String? = null,
    ): HttpResponse<String> {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(java.time.Duration.ofSeconds(15))
        if (access != null) request.header("Authorization", "Bearer $access")
        if (rawAuthorization != null) request.header("Authorization", rawAuthorization)
        if (operationId != null) request.header("Idempotency-Key", operationId.toString())
        if (body != null) request.header("Content-Type", "application/json")
        return client.send(
            request
                .method(
                    method,
                    body?.let(HttpRequest.BodyPublishers::ofString)
                        ?: HttpRequest.BodyPublishers.noBody(),
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun refresh(client: HttpClient, token: String, id: UUID): HttpResponse<String> =
        native(
            client,
            "/api/v1/auth/native/refresh",
            method = "POST",
            body = json.writeValueAsString(mapOf("refreshToken" to token)),
            operationId = id,
        )

    @Test
    fun exchangesAreReplayableAndBearerAuthenticationCannotFallBackToCookies() {
        val pending = fixture()
        assertEquals(403, exchange(pending).statusCode())
        val f = enroll(pending).fixture
        val id = UUID.randomUUID()
        val exchanged = exchange(f, id)
        val credentials = tokens(exchanged)
        assertEquals(exchanged.body(), exchange(f, id).body())
        assertEquals(409, exchange(f, id, "Changed device").statusCode())
        assertTrue(exchanged.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        val record =
            database()
                .queryForMap(
                    "select access_hash,refresh_hash,exchange_encrypted from native_sessions where id=?",
                    credentials.id,
                )
        assertNotEquals(credentials.access, record["access_hash"])
        assertNotEquals(credentials.refresh, record["refresh_hash"])
        assertFalse(record["exchange_encrypted"].toString().contains(credentials.refresh))
        val mobile = client()
        val me = native(mobile, "/api/v1/me", credentials.access)
        assertEquals(200, me.statusCode(), me.body())
        assertEquals(
            f.account.toString(),
            json.readTree(me.body()).get("account").get("id").asString(),
        )
        assertTrue(me.headers().allValues("Set-Cookie").isEmpty())
        assertEquals(401, get(mobile, "/api/v1/me").statusCode())
        val body =
            """{"code":"N${UUID.randomUUID().toString().take(8)}","name":"Native test","timezone":"UTC"}"""
        val created =
            native(mobile, "/api/v1/companies", credentials.access, "POST", body, UUID.randomUUID())
        assertEquals(200, created.statusCode(), created.body())
        assertEquals(403, post(f.client, "/api/v1/companies", body).statusCode())
        assertEquals(
            401,
            native(f.client, "/api/v1/me", rawAuthorization = "Bearer bad").statusCode(),
        )
        assertEquals(
            401,
            native(f.client, "/api/v1/me", rawAuthorization = "Basic ignored").statusCode(),
        )
        assertEquals(
            401,
            native(
                    f.client,
                    "/api/v1/me",
                    credentials.access,
                    rawAuthorization = "Bearer duplicate",
                )
                .statusCode(),
        )
        assertEquals(200, get(f.client, "/api/v1/me").statusCode())
        assertEquals(
            403,
            native(
                    mobile,
                    "/api/v1/auth/native/exchange",
                    credentials.access,
                    "POST",
                    """{"deviceName":"Other"}""",
                    UUID.randomUUID(),
                )
                .statusCode(),
        )
        assertEquals(
            403,
            native(
                    mobile,
                    "/api/v1/auth/mfa/enrollment",
                    credentials.access,
                    "POST",
                    "{}",
                    UUID.randomUUID(),
                )
                .statusCode(),
        )
        assertEquals(
            403,
            command(
                    f.client,
                    "/api/v1/auth/native/mfa/verify",
                    """{"code":"123456"}""",
                    f.csrf,
                    UUID.randomUUID(),
                )
                .statusCode(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from mfa_enrollments where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
    }

    @Test
    fun concurrentIdenticalRefreshesReturnOneCredentialPairAndReuseRevocationCommits() {
        val f = enroll(fixture()).fixture
        val original = tokens(exchange(f))
        val independent = tokens(exchange(f))
        val mobile = client()
        clock.set(clock.instant().plusSeconds(11))
        val operation = UUID.randomUUID()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val responses =
            Executors.newFixedThreadPool(2).use { pool ->
                val calls =
                    (1..2).map {
                        pool.submit<HttpResponse<String>> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            refresh(mobile, original.refresh, operation)
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                calls.map { it.get(15, TimeUnit.SECONDS) }
            }
        val rotated = tokens(responses.first())
        assertEquals(responses.first().body(), responses.last().body())
        assertEquals(responses.first().body(), refresh(mobile, original.refresh, operation).body())
        assertNotEquals(original.refresh, rotated.refresh)
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select version from native_sessions where id=?",
                    Long::class.java,
                    original.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from consumed_refresh_tokens where session_id=?",
                    Int::class.java,
                    original.id,
                ),
        )
        assertEquals(401, native(mobile, "/api/v1/me", original.access).statusCode())
        assertEquals(200, native(mobile, "/api/v1/me", rotated.access).statusCode())
        val stolen = refresh(mobile, original.refresh, UUID.randomUUID())
        assertEquals(401, stolen.statusCode(), stolen.body())
        assertNotNull(
            database()
                .queryForObject(
                    "select revoked_at from native_sessions where id=?",
                    java.sql.Timestamp::class.java,
                    original.id,
                )
        )
        assertEquals(401, native(mobile, "/api/v1/me", rotated.access).statusCode())
        assertEquals(401, refresh(mobile, rotated.refresh, UUID.randomUUID()).statusCode())
        assertEquals(200, native(mobile, "/api/v1/me", independent.access).statusCode())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='identity.refresh_reuse_detected'",
                    Int::class.java,
                    original.id,
                ),
        )
    }

    @Test
    fun competingRefreshOperationsDoNotLeaveTheWinningCredentialActiveAfterReuse() {
        val original = tokens(exchange(enroll(fixture()).fixture))
        val mobile = client()
        clock.set(clock.instant().plusSeconds(11))
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val responses =
            Executors.newFixedThreadPool(2).use { pool ->
                val calls =
                    (1..2).map {
                        pool.submit<HttpResponse<String>> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            refresh(mobile, original.refresh, UUID.randomUUID())
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                calls.map { it.get(15, TimeUnit.SECONDS) }
            }
        assertEquals(listOf(200, 401), responses.map { it.statusCode() }.sorted())
        val winner = tokens(responses.first { it.statusCode() == 200 })
        assertEquals(401, native(mobile, "/api/v1/me", winner.access).statusCode())
    }

    @Test
    fun failedRefreshAuditRollsBackTheTokensReceiptAndVersion() {
        val f = enroll(fixture()).fixture
        val original = tokens(exchange(f))
        val mobile = client()
        clock.set(clock.instant().plusSeconds(11))
        val operation = UUID.randomUUID()
        database()
            .execute(
                """CREATE FUNCTION test_block_native_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id='${f.account}'::uuid AND NEW.action='identity.native_session_refreshed' THEN RAISE EXCEPTION 'deliberate audit failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$"""
            )
        database()
            .execute(
                "CREATE TRIGGER test_native_audit_failure BEFORE INSERT ON audit_entries FOR EACH ROW EXECUTE FUNCTION test_block_native_audit()"
            )
        try {
            val failed = refresh(mobile, original.refresh, operation)
            assertEquals(409, failed.statusCode(), failed.body())
            assertFalse(failed.body().contains("deliberate"))
            assertEquals(
                0L,
                database()
                    .queryForObject(
                        "select version from native_sessions where id=?",
                        Long::class.java,
                        original.id,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from consumed_refresh_tokens where session_id=?",
                        Int::class.java,
                        original.id,
                    ),
            )
            assertEquals(200, native(mobile, "/api/v1/me", original.access).statusCode())
        } finally {
            database().execute("DROP TRIGGER test_native_audit_failure ON audit_entries")
            database().execute("DROP FUNCTION test_block_native_audit()")
        }
        val retried = tokens(refresh(mobile, original.refresh, operation))
        assertEquals(200, native(mobile, "/api/v1/me", retried.access).statusCode())
    }

    @Test
    fun sessionOwnershipCredentialRevocationAndExpiryAreRechecked() {
        val f = enroll(fixture()).fixture
        val original = tokens(exchange(f))
        val stranger = enroll(fixture()).fixture
        val strangerOriginal = tokens(exchange(stranger))
        val expiring = tokens(exchange(stranger))
        val mobile = client()
        assertEquals(
            404,
            command(
                    stranger.client,
                    "/api/v1/auth/native/sessions/${original.id}",
                    "{}",
                    stranger.csrf,
                    UUID.randomUUID(),
                    "DELETE",
                )
                .statusCode(),
        )
        val owned = json.readTree(get(f.client, "/api/v1/auth/native/sessions").body())
        assertEquals(1, owned.size())
        assertEquals(original.id.toString(), owned[0].get("id").asString())
        clock.set(clock.instant().plusSeconds(600))
        assertEquals(401, native(mobile, "/api/v1/me", original.access).statusCode())
        val renewed = tokens(refresh(mobile, original.refresh, UUID.randomUUID()))
        assertEquals(200, native(mobile, "/api/v1/me", renewed.access).statusCode())
        database()
            .update("update accounts set security_version=security_version+1 where id=?", f.account)
        assertEquals(401, native(mobile, "/api/v1/me", renewed.access).statusCode())
        assertEquals(401, refresh(mobile, renewed.refresh, UUID.randomUUID()).statusCode())
        val other = tokens(refresh(mobile, strangerOriginal.refresh, UUID.randomUUID()))
        assertEquals(
            204,
            native(mobile, "/api/v1/auth/native/sessions/${other.id}", other.access, "DELETE")
                .statusCode(),
        )
        assertEquals(401, native(mobile, "/api/v1/me", other.access).statusCode())
        clock.set(clock.instant().plusSeconds(30L * 86400))
        assertEquals(401, refresh(mobile, expiring.refresh, UUID.randomUUID()).statusCode())
    }

    @Test
    fun mobileMfaRenewalRotatesCredentialsWithoutCreatingAWebSession() {
        val enrolled = enroll(fixture())
        val original = tokens(exchange(enrolled.fixture))
        val mobile = client()
        clock.set(clock.instant().plusSeconds(12L * 3600 + 1))
        val refreshed = tokens(refresh(mobile, original.refresh, UUID.randomUUID()))
        val denied = native(mobile, "/api/v1/test/assurance", refreshed.access)
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals("mfa_required", json.readTree(denied.body()).get("code").asString())
        val proof =
            native(
                mobile,
                "/api/v1/auth/native/mfa/verify",
                refreshed.access,
                "POST",
                json.writeValueAsString(mapOf("code" to totp(enrolled.secret, clock.instant()))),
                UUID.randomUUID(),
            )
        val elevated = tokens(proof)
        assertTrue(proof.headers().allValues("Set-Cookie").isEmpty())
        assertEquals(200, native(mobile, "/api/v1/test/assurance", elevated.access).statusCode())
        assertEquals(401, native(mobile, "/api/v1/me", refreshed.access).statusCode())
        val regenerated =
            native(
                mobile,
                "/api/v1/auth/native/mfa/recovery-codes",
                elevated.access,
                "POST",
                "{}",
                UUID.randomUUID(),
            )
        val newest = tokens(regenerated)
        assertEquals(10, json.readTree(regenerated.body()).get("recoveryCodes").size())
        assertEquals(401, get(enrolled.fixture.client, "/api/v1/me").statusCode())
        assertEquals(200, native(mobile, "/api/v1/test/assurance", newest.access).statusCode())
    }

    @Test
    fun boundedFamiliesRotationFrequencyAndRecentAuthenticationPreventUnboundedGrowth() {
        val f = enroll(fixture()).fixture
        val first = tokens(exchange(f))
        val mobile = client()
        val tooSoon = refresh(mobile, first.refresh, UUID.randomUUID())
        assertEquals(429, tooSoon.statusCode(), tooSoon.body())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from consumed_refresh_tokens where session_id=?",
                    Int::class.java,
                    first.id,
                ),
        )
        repeat(9) { tokens(exchange(f)) }
        assertEquals(409, exchange(f).statusCode())
        assertEquals(
            10,
            database()
                .queryForObject(
                    "select count(*) from native_sessions where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
        assertEquals(
            204,
            native(mobile, "/api/v1/auth/native/sessions/${first.id}", first.access, "DELETE")
                .statusCode(),
        )
        val replacement = tokens(exchange(f))
        clock.set(clock.instant().plusSeconds(601))
        val stale = exchange(f)
        assertEquals(403, stale.statusCode(), stale.body())
        assertEquals(
            "recent_authentication_required",
            json.readTree(stale.body()).get("code").asString(),
        )
        database().update("update native_sessions set version=5000 where id=?", replacement.id)
        assertEquals(401, refresh(mobile, replacement.refresh, UUID.randomUUID()).statusCode())
        assertNotNull(
            database()
                .queryForObject(
                    "select revoked_at from native_sessions where id=?",
                    java.sql.Timestamp::class.java,
                    replacement.id,
                )
        )
    }

    @Test
    fun expiredReplayAndRepeatedSessionCreationDoNotBypassCredentialLimits() {
        val f = enroll(fixture()).fixture
        val original = tokens(exchange(f))
        val mobile = client()
        clock.set(clock.instant().plusSeconds(11))
        val operation = UUID.randomUUID()
        val successor = tokens(refresh(mobile, original.refresh, operation))
        clock.set(clock.instant().plusSeconds(121))
        assertEquals(401, refresh(mobile, original.refresh, operation).statusCode())
        assertEquals(401, native(mobile, "/api/v1/me", successor.access).statusCode())
        val at = java.sql.Timestamp.from(clock.instant())
        val until = java.sql.Timestamp.from(clock.instant().plusSeconds(86400))
        database()
            .update(
                """insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,revoked_at)
            select gen_random_uuid(), ?, md5(random()::text)||md5(random()::text), ?, ?, ? from generate_series(1,99)""",
                f.account,
                at,
                until,
                at,
            )
        val limited = exchange(f)
        assertEquals(429, limited.statusCode(), limited.body())
        assertEquals(
            "native_session_creation_limit",
            json.readTree(limited.body()).get("code").asString(),
        )
        assertEquals(
            100,
            database()
                .queryForObject(
                    "select count(*) from native_sessions where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
    }
}

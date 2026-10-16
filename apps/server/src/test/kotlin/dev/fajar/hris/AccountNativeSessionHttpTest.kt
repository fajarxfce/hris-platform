package dev.fajar.hris

import dev.fajar.hris.identity.data.datasources.NativeSessionDataSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AccountNativeSessionHttpTest : MfaApiFixture() {
    @Autowired private lateinit var source: NativeSessionDataSource

    private data class Tokens(val id: UUID, val access: String, val refresh: String) {
        override fun toString() = "Tokens(<redacted>)"
    }

    private fun exchange(f: Fixture, name: String = "Owned test device"): Tokens {
        val response =
            command(
                f.client,
                "/api/v1/auth/native/exchange",
                json.writeValueAsString(mapOf("deviceName" to name)),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        val value = json.readTree(response.body())
        return Tokens(
            UUID.fromString(value["sessionId"].asString()),
            value["accessToken"].asString(),
            value["refreshToken"].asString(),
        )
    }

    private fun request(
        browser: HttpClient,
        path: String,
        method: String = "GET",
        csrf: String? = null,
        bearer: String? = null,
        body: String? = null,
    ): HttpResponse<String> {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(15))
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf)
        if (bearer != null) request.header("Authorization", "Bearer $bearer")
        if (body != null) request.header("Content-Type", "application/json")
        if (path == "/api/v1/auth/native/refresh")
            request.header("Idempotency-Key", UUID.randomUUID().toString())
        return browser.send(
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

    private fun path(account: UUID, session: UUID? = null) =
        "/api/v1/identity/accounts/$account/native-sessions" + (session?.let { "/$it" } ?: "")

    private fun audits(session: UUID): Int =
        requireNotNull(
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='identity.native_session_revoked'",
                    Int::class.java,
                    session,
                )
        )

    @Test
    fun platformReadsReturnOnlyTheSelectedAccountsMetadataWithoutAcquiringTokens() {
        val admin = enroll(fixture()).fixture
        val target = enroll(fixture()).fixture
        val other = enroll(fixture()).fixture
        val first = exchange(target, "Owned Android")
        val second = exchange(target, "Owned iOS")
        exchange(other)
        val listed = get(admin.client, path(target.account))
        assertEquals(200, listed.statusCode(), listed.body())
        assertTrue(listed.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        val rows = json.readTree(listed.body()).iterator().asSequence().toList()
        assertEquals(
            setOf(first.id.toString(), second.id.toString()),
            rows.map { it["id"].asString() }.toSet(),
        )
        for (row in rows) assertEquals(
            setOf("id", "deviceName", "createdAt", "expiresAt"),
            row.propertyNames().toSet(),
        )
        for (row in source.list(target.account, clock.instant(), 50)) {
            assertNull(row.accessHash)
            assertNull(row.refreshHash)
            assertNull(row.exchangeEncrypted)
        }
        assertEquals(404, get(admin.client, path(UUID.randomUUID())).statusCode())
        assertEquals(401, get(client(), path(target.account)).statusCode())
        database()
            .update(
                "delete from platform_permissions where account_id=? and permission='identity.manage'",
                target.account,
            )
        assertEquals(403, get(target.client, path(target.account)).statusCode())
        assertEquals(
            403,
            request(target.client, path(other.account, first.id), "DELETE", target.csrf)
                .statusCode(),
        )
        assertEquals(200, get(target.client, "/api/v1/auth/native/sessions").statusCode())
    }

    @Test
    fun repeatedRevocationEndsOneSessionAndPreservesOtherDevicesAccountsAndWebAccess() {
        val admin = enroll(fixture()).fixture
        val target = enroll(fixture()).fixture
        val other = enroll(fixture()).fixture
        val ended = exchange(target)
        val retained = exchange(target)
        val independent = exchange(other)
        repeat(2) {
            val result = request(admin.client, path(target.account, ended.id), "DELETE", admin.csrf)
            assertEquals(204, result.statusCode(), result.body())
            assertEquals("", result.body())
        }
        assertEquals(1, audits(ended.id))
        assertEquals(401, request(client(), "/api/v1/me", bearer = ended.access).statusCode())
        assertEquals(
            401,
            request(
                    client(),
                    "/api/v1/auth/native/refresh",
                    "POST",
                    body = json.writeValueAsString(mapOf("refreshToken" to ended.refresh)),
                )
                .statusCode(),
        )
        assertEquals(200, request(client(), "/api/v1/me", bearer = retained.access).statusCode())
        assertEquals(200, request(client(), "/api/v1/me", bearer = independent.access).statusCode())
        assertEquals(200, get(target.client, "/api/v1/me").statusCode())
        assertEquals(200, get(admin.client, "/api/v1/me").statusCode())
        val remaining = json.readTree(get(admin.client, path(target.account)).body())
        assertEquals(1, remaining.size())
        assertEquals(retained.id.toString(), remaining[0]["id"].asString())
    }

    @Test
    fun csrfAndExactAccountOwnershipAreRequiredBeforeAnyRevocation() {
        val admin = enroll(fixture()).fixture
        val target = enroll(fixture()).fixture
        val retained = exchange(target)
        assertEquals(
            403,
            request(admin.client, path(target.account, retained.id), "DELETE").statusCode(),
        )
        val mismatched =
            request(admin.client, path(admin.account, retained.id), "DELETE", admin.csrf)
        assertEquals(404, mismatched.statusCode(), mismatched.body())
        assertEquals(
            "native_session_not_found",
            json.readTree(mismatched.body())["code"].asString(),
        )
        assertEquals(
            404,
            request(admin.client, path(UUID.randomUUID(), retained.id), "DELETE", admin.csrf)
                .statusCode(),
        )
        assertEquals(200, request(client(), "/api/v1/me", bearer = retained.access).statusCode())
        assertEquals(0, audits(retained.id))
    }

    @Test
    fun administrationRequiresMfaAndRecentProofForRevocation() {
        val pending = fixture()
        val target = enroll(fixture()).fixture
        val retained = exchange(target)
        assertEquals(403, get(pending.client, path(target.account)).statusCode())
        assertEquals(
            403,
            request(pending.client, path(target.account, retained.id), "DELETE", pending.csrf)
                .statusCode(),
        )
        val enrolled = enroll(pending)
        val admin = enrolled.fixture
        clock.set(clock.instant().plusSeconds(601))
        assertEquals(200, get(admin.client, path(target.account)).statusCode())
        val stale = request(admin.client, path(target.account, retained.id), "DELETE", admin.csrf)
        assertEquals(403, stale.statusCode(), stale.body())
        assertEquals(
            "recent_authentication_required",
            json.readTree(stale.body())["code"].asString(),
        )
        assertEquals(0, audits(retained.id))
        assertEquals(200, verify(admin, totp(enrolled.secret, clock.instant())).statusCode())
        assertEquals(
            204,
            request(admin.client, path(target.account, retained.id), "DELETE", csrf(admin.client))
                .statusCode(),
        )
    }

    @Test
    fun competingAndBearerAdministrativeDeletesRemainIdempotent() {
        val admin = enroll(fixture()).fixture
        val target = enroll(fixture()).fixture
        val retained = exchange(target)
        val administrator = exchange(admin)
        val mobile = client()
        val listed = request(mobile, path(target.account), bearer = administrator.access)
        assertEquals(200, listed.statusCode(), listed.body())
        assertTrue(listed.headers().allValues("Set-Cookie").isEmpty())
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val pending =
                List(2) {
                    executor.submit<HttpResponse<String>> {
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        request(
                            mobile,
                            path(target.account, retained.id),
                            "DELETE",
                            bearer = administrator.access,
                        )
                    }
                }
            start.countDown()
            pending.forEach { operation ->
                val response = operation.get(10, TimeUnit.SECONDS)
                assertEquals(204, response.statusCode(), response.body())
                assertTrue(response.headers().allValues("Set-Cookie").isEmpty())
            }
        }
        assertEquals(1, audits(retained.id))
        assertEquals(200, request(mobile, "/api/v1/me", bearer = administrator.access).statusCode())
    }
}

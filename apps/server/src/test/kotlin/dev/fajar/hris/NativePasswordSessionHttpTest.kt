package dev.fajar.hris

import java.net.CookieManager
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

class NativePasswordSessionHttpTest : MfaApiFixture() {
    private fun request(
        mobile: HttpClient,
        path: String,
        body: Any? = null,
        access: String? = null,
        operation: UUID? = null,
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(15))
        access?.let { builder.header("Authorization", "Bearer $it") }
        operation?.let { builder.header("Idempotency-Key", it.toString()) }
        if (body != null)
            builder
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
        else builder.GET()
        return mobile.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun signIn(
        mobile: HttpClient,
        f: Fixture,
        operation: UUID = UUID.randomUUID(),
        deviceName: String = "Test Android",
    ) =
        request(
            mobile,
            "/api/v1/auth/native/login",
            mapOf(
                "email" to f.email,
                "password" to "Testing-password-123!",
                "deviceName" to deviceName,
            ),
            operation = operation,
        )

    private fun access(response: HttpResponse<String>): String {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())["credentials"]["accessToken"].asString()
    }

    @Test
    fun passwordLoginReplaysWithoutCookiesAndPendingMfaCannotReadCompanyScope() {
        val f = fixture()
        val mobile = client()
        val operation = UUID.randomUUID()
        val signedIn = signIn(mobile, f, operation)
        val token = access(signedIn)
        assertEquals(signedIn.body(), signIn(mobile, f, operation).body())
        assertEquals(409, signIn(mobile, f, operation, "Different device").statusCode())
        assertEquals(
            f.account.toString(),
            json.readTree(signedIn.body())["account"]["id"].asString(),
        )
        assertTrue(signedIn.headers().allValues("Set-Cookie").isEmpty())
        assertTrue(signedIn.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        assertTrue(
            (mobile.cookieHandler().orElseThrow() as CookieManager).cookieStore.cookies.isEmpty()
        )
        val current = request(mobile, "/api/v1/me", access = token)
        assertEquals(200, current.statusCode(), current.body())
        val profile = json.readTree(current.body())
        assertTrue(profile["assurance"]["required"].asBoolean())
        assertTrue(profile["assurance"]["setupAvailable"].asBoolean())
        assertEquals(0, profile["companies"].size())
        assertEquals(0, profile["permissions"].size())
        val denied =
            request(
                mobile,
                "/api/v1/companies",
                mapOf("code" to "NATIVE", "name" to "Native test", "timezone" to "UTC"),
                token,
                UUID.randomUUID(),
            )
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals("mfa_setup_required", json.readTree(denied.body())["code"].asString())
        assertEquals(401, get(mobile, "/api/v1/me").statusCode())
    }

    @Test
    fun nativeEnrollmentElevatesOnlyItsSessionAndReturnsRecoveryCodesOnce() {
        val f = fixture()
        val mobile = client()
        val pending = access(signIn(mobile, f))
        val enrollmentId = UUID.randomUUID()
        val enrollment =
            request(
                mobile,
                "/api/v1/auth/native/mfa/enrollment",
                emptyMap<String, String>(),
                pending,
                enrollmentId,
            )
        assertEquals(200, enrollment.statusCode(), enrollment.body())
        assertEquals(
            enrollment.body(),
            request(
                    mobile,
                    "/api/v1/auth/native/mfa/enrollment",
                    emptyMap<String, String>(),
                    pending,
                    enrollmentId,
                )
                .body(),
        )
        val secret = json.readTree(enrollment.body())["secret"].asString()
        val confirmed =
            request(
                mobile,
                "/api/v1/auth/native/mfa/enrollment/confirm",
                mapOf("operationId" to enrollmentId, "code" to totp(secret, clock.instant())),
                pending,
                UUID.randomUUID(),
            )
        val verified = access(confirmed)
        assertEquals(10, json.readTree(confirmed.body())["recoveryCodes"].size())
        assertEquals(401, request(mobile, "/api/v1/me", access = pending).statusCode())
        val profile = request(mobile, "/api/v1/me", access = verified)
        assertEquals(200, profile.statusCode(), profile.body())
        assertTrue(json.readTree(profile.body())["assurance"]["verified"].asBoolean())
        assertTrue(json.readTree(profile.body())["permissions"].size() > 0)
        assertEquals(401, get(f.client, "/api/v1/me").statusCode())
        assertEquals(
            403,
            request(
                    mobile,
                    "/api/v1/auth/mfa/enrollment",
                    emptyMap<String, String>(),
                    verified,
                    UUID.randomUUID(),
                )
                .statusCode(),
        )
        assertTrue(confirmed.headers().allValues("Set-Cookie").isEmpty())
    }

    @Test
    fun anEnrolledAccountMustVerifyAgainAfterEachPasswordLogin() {
        val enrolled = enroll(fixture())
        val mobile = client()
        clock.set(clock.instant().plusSeconds(30))
        val pending = access(signIn(mobile, enrolled.fixture))
        val current = request(mobile, "/api/v1/me", access = pending)
        assertFalse(json.readTree(current.body())["assurance"]["verified"].asBoolean())
        assertEquals(0, json.readTree(current.body())["permissions"].size())
        val verified =
            access(
                request(
                    mobile,
                    "/api/v1/auth/native/mfa/verify",
                    mapOf("code" to totp(enrolled.secret, clock.instant()), "recovery" to false),
                    pending,
                    UUID.randomUUID(),
                )
            )
        assertTrue(
            json
                .readTree(request(mobile, "/api/v1/me", access = verified).body())["assurance"][
                    "verified"]
                .asBoolean()
        )
        assertEquals(401, request(mobile, "/api/v1/me", access = pending).statusCode())
    }

    @Test
    fun loginRequiresItsOwnPasswordAndAnOperationKeyEvenWithAnExistingCookie() {
        val f = enroll(fixture()).fixture
        val wrong = mapOf("email" to f.email, "password" to "wrong", "deviceName" to "Android")
        val denied =
            request(f.client, "/api/v1/auth/native/login", wrong, operation = UUID.randomUUID())
        assertEquals(401, denied.statusCode(), denied.body())
        assertEquals("invalid_credentials", json.readTree(denied.body())["code"].asString())
        assertEquals(400, request(f.client, "/api/v1/auth/native/login", wrong).statusCode())
        assertEquals(422, signIn(client(), f, deviceName = "").statusCode())
        assertEquals(
            401,
            request(client(), "/api/v1/auth/native/login", wrong, "invalid", UUID.randomUUID())
                .statusCode(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from native_sessions where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
        assertEquals(200, get(f.client, "/api/v1/me").statusCode())
    }

    @Test
    fun concurrentRetriesCreateOneNativeSessionAndOneIssuanceAudit() {
        val f = fixture()
        val mobile = client()
        val operation = UUID.randomUUID()
        val start = CountDownLatch(1)
        val ready = CountDownLatch(4)
        val outcomes =
            Executors.newFixedThreadPool(4).use { executor ->
                val calls =
                    List(4) {
                        executor.submit<HttpResponse<String>> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            signIn(mobile, f, operation)
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                calls.map { it.get(20, TimeUnit.SECONDS) }
            }
        outcomes.forEach { assertEquals(200, it.statusCode(), it.body()) }
        assertEquals(1, outcomes.map { it.body() }.distinct().size)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where actor_id=? and action='identity.native_session_created'",
                    Int::class.java,
                    f.account,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from native_sessions where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
    }

    @Test
    fun sessionLimitAndCredentialRevocationAlsoApplyBeforeMfaEnrollment() {
        val f = fixture()
        val mobile = client()
        val first = access(signIn(mobile, f))
        repeat(9) { access(signIn(mobile, f)) }
        val limited = signIn(mobile, f)
        assertEquals(409, limited.statusCode(), limited.body())
        assertEquals("native_session_limit", json.readTree(limited.body())["code"].asString())
        database()
            .update("update accounts set security_version=security_version+1 where id=?", f.account)
        assertEquals(401, request(mobile, "/api/v1/me", access = first).statusCode())
    }
}

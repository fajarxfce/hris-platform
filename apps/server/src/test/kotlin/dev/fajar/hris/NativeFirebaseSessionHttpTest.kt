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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@Import(NativeFirebaseTestConfiguration::class)
class NativeFirebaseSessionHttpTest : MfaApiFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun firebase(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_FIREBASE_AUTH_ENABLED") { true }
            registry.add("HRIS_FIREBASE_PROJECT_ID") { "cmp-fajar" }
        }
    }

    @Autowired private lateinit var keys: FirebaseTestKeys
    private val endpoint = "/api/v1/auth/native/firebase/login"

    private fun request(
        mobile: HttpClient,
        path: String,
        body: Any? = null,
        access: String? = null,
        operation: UUID? = null,
        method: String = "POST",
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(15))
        access?.let { builder.header("Authorization", "Bearer $it") }
        operation?.let { builder.header("Idempotency-Key", it.toString()) }
        if (body != null)
            builder
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
        else builder.GET()
        return mobile.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun signIn(
        mobile: HttpClient,
        token: String,
        operation: UUID = UUID.randomUUID(),
        device: String = "Firebase Android",
    ) =
        request(
            mobile,
            endpoint,
            mapOf("idToken" to token, "deviceName" to device),
            operation = operation,
        )

    private fun approved(f: Fixture, subject: String = UUID.randomUUID().toString()): String {
        database()
            .update(
                "insert into oidc_identities(issuer,subject,account_id) values(?,?,?)",
                "https://securetoken.google.com/cmp-fajar",
                subject,
                f.account,
            )
        return subject
    }

    private fun access(response: HttpResponse<String>): String {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())["credentials"]["accessToken"].asString()
    }

    private fun sessions(f: Fixture): Int =
        database()
            .queryForObject(
                "select count(*) from native_sessions where account_id=?",
                Int::class.java,
                f.account,
            )!!

    @Test
    fun bothProvidersExchangeApprovedSubjectsWithoutCookiesOrTrustedFirebaseRoles() {
        val f = fixture()
        val subject = approved(f)
        val mobile = client()
        val config = get(mobile, "/api/v1/auth/native/firebase")
        assertEquals(200, config.statusCode(), config.body())
        val settings = json.readTree(config.body())
        assertTrue(settings["enabled"].asBoolean())
        assertEquals("cmp-fajar", settings["projectId"].asString())
        assertEquals(
            listOf("password", "google.com"),
            (0 until settings["providers"].size()).map { settings["providers"][it].asString() },
        )
        for (provider in listOf("password", "google.com")) {
            val idToken =
                keys.token(
                    subject,
                    clock.instant(),
                    mapOf(
                        "firebase" to
                            mapOf(
                                "sign_in_provider" to provider,
                                "sign_in_second_factor" to "phone",
                            ),
                        "roles" to listOf("identity.manage"),
                        "email_verified" to true,
                    ),
                )
            val operation = UUID.randomUUID()
            val signedIn = signIn(mobile, idToken, operation)
            val token = access(signedIn)
            assertEquals(signedIn.body(), signIn(mobile, idToken, operation).body())
            assertEquals(409, signIn(mobile, idToken, operation, "Changed device").statusCode())
            assertEquals(
                f.account.toString(),
                json.readTree(signedIn.body())["account"]["id"].asString(),
            )
            assertTrue(signedIn.headers().allValues("Set-Cookie").isEmpty())
            assertTrue(
                signedIn.headers().firstValue("Cache-Control").orElse("").contains("no-store")
            )
            val current = request(mobile, "/api/v1/me", access = token)
            assertEquals(200, current.statusCode(), current.body())
            val profile = json.readTree(current.body())
            assertTrue(profile["assurance"]["required"].asBoolean())
            assertTrue(profile["assurance"]["setupAvailable"].asBoolean())
            assertEquals(0, profile["permissions"].size())
            assertEquals(0, profile["companies"].size())
            val denied =
                request(
                    mobile,
                    "/api/v1/companies",
                    mapOf("code" to "FB", "name" to "Firebase test", "timezone" to "UTC"),
                    token,
                    UUID.randomUUID(),
                )
            assertEquals(403, denied.statusCode(), denied.body())
            assertEquals("mfa_setup_required", json.readTree(denied.body())["code"].asString())
            assertEquals(401, request(mobile, "/api/v1/me", access = idToken).statusCode())
        }
        assertEquals(2, sessions(f))
        assertTrue(
            (mobile.cookieHandler().orElseThrow() as CookieManager).cookieStore.cookies.isEmpty()
        )
        assertEquals(401, get(mobile, "/api/v1/me").statusCode())
    }

    @Test
    fun matchingEmailDoesNotLinkAnUnapprovedFirebaseSubject() {
        val f = fixture()
        val token =
            keys.token(
                UUID.randomUUID().toString(),
                clock.instant(),
                mapOf("email" to f.email, "email_verified" to true),
            )
        assertEquals(401, signIn(client(), token).statusCode())
        assertEquals(0, sessions(f))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from oidc_identities where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
    }

    @Test
    fun invalidClaimsSignaturesAndUnavailableKeysCannotCreateSessions() {
        val f = fixture()
        val subject = approved(f)
        val mobile = client()
        val variants =
            listOf(
                mapOf("iss" to "https://attacker.example.test"),
                mapOf("aud" to "another-project"),
                mapOf("auth_time" to clock.instant().minusSeconds(601).epochSecond),
                mapOf("firebase" to mapOf("sign_in_provider" to "anonymous")),
                mapOf(
                    "firebase" to
                        mapOf("sign_in_provider" to "password", "tenant" to "another-tenant")
                ),
            )
        variants.forEach { claims ->
            val denied = signIn(mobile, keys.token(subject, clock.instant(), claims))
            assertEquals(401, denied.statusCode(), denied.body())
        }
        assertEquals(
            401,
            signIn(mobile, FirebaseTestKeys().token(subject, clock.instant())).statusCode(),
        )
        try {
            keys.unavailable = true
            val unavailable = signIn(mobile, keys.token(subject, clock.instant()))
            assertEquals(503, unavailable.statusCode(), unavailable.body())
            assertEquals(
                "firebase_sign_in_unavailable",
                json.readTree(unavailable.body())["code"].asString(),
            )
        } finally {
            keys.unavailable = false
        }
        assertEquals(0, sessions(f))
    }

    @Test
    fun approvedAdministrationLinkStillRequiresHrisMfaAndRevocationInvalidatesItsSessions() {
        val enrolled = enroll(fixture())
        val f = enrolled.fixture
        val subject = UUID.randomUUID().toString()
        val id = UUID.randomUUID()
        val path = "/api/v1/identity/accounts/${f.account}/oidc/$id"
        fun link(active: Boolean, version: Long?) =
            mapOf(
                "issuer" to "https://securetoken.google.com/cmp-fajar",
                "subject" to subject,
                "active" to active,
                "expectedVersion" to version,
                "reason" to "Verified Firebase UID",
            )
        val created =
            command(
                f.client,
                path,
                json.writeValueAsString(link(true, null)),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, created.statusCode(), created.body())
        assertEquals(401, get(f.client, "/api/v1/me").statusCode())
        val version = json.readTree(created.body())["version"].asLong()
        clock.set(clock.instant().plusSeconds(30))
        val mobile = client()
        val firebase = keys.token(subject, clock.instant())
        val pending = access(signIn(mobile, firebase))
        val current = request(mobile, "/api/v1/me", access = pending)
        assertFalse(json.readTree(current.body())["assurance"]["verified"].asBoolean())
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
        assertEquals(401, request(mobile, "/api/v1/me", access = pending).statusCode())
        assertTrue(
            json
                .readTree(request(mobile, "/api/v1/me", access = verified).body())["assurance"][
                    "verified"]
                .asBoolean()
        )
        val revoked =
            request(mobile, path, link(false, version), verified, UUID.randomUUID(), "PUT")
        assertEquals(200, revoked.statusCode(), revoked.body())
        assertEquals(401, request(mobile, "/api/v1/me", access = verified).statusCode())
        assertEquals(401, signIn(mobile, firebase).statusCode())
    }

    @Test
    fun validBrowserCookieCannotReplaceFirebaseProofOrIdempotency() {
        val f = enroll(fixture()).fixture
        val subject = approved(f)
        val invalid = mapOf("idToken" to "x".repeat(64), "deviceName" to "Android")
        assertEquals(
            401,
            request(f.client, endpoint, invalid, operation = UUID.randomUUID()).statusCode(),
        )
        assertEquals(400, request(f.client, endpoint, invalid).statusCode())
        assertEquals(
            422,
            signIn(f.client, keys.token(subject, clock.instant()), device = "").statusCode(),
        )
        assertEquals(0, sessions(f))
        assertEquals(200, get(f.client, "/api/v1/me").statusCode())
    }

    @Test
    fun subjectAttemptBudgetAlsoBoundsIdempotentRetries() {
        val f = fixture()
        val subject = approved(f)
        val mobile = client()
        val token = keys.token(subject, clock.instant())
        val operation = UUID.randomUUID()
        repeat(10) { access(signIn(mobile, token, operation)) }
        val limited = signIn(mobile, token, operation)
        assertEquals(429, limited.statusCode(), limited.body())
        assertEquals("sign_in_rate_limited", json.readTree(limited.body())["code"].asString())
        assertEquals(1, sessions(f))
    }

    @Test
    fun concurrentRetriesCreateOneNativeFamilyAndIssuanceAudit() {
        val f = fixture()
        val token = keys.token(approved(f), clock.instant())
        val mobile = client()
        val operation = UUID.randomUUID()
        val ready = CountDownLatch(4)
        val start = CountDownLatch(1)
        val outcomes =
            Executors.newFixedThreadPool(4).use { executor ->
                val calls =
                    List(4) {
                        executor.submit<HttpResponse<String>> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            signIn(mobile, token, operation)
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                calls.map { it.get(20, TimeUnit.SECONDS) }
            }
        outcomes.forEach { assertEquals(200, it.statusCode(), it.body()) }
        assertEquals(1, outcomes.map { it.body() }.distinct().size)
        assertEquals(1, sessions(f))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where actor_id=? and action='identity.native_session_created'",
                    Int::class.java,
                    f.account,
                ),
        )
    }
}

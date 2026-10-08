package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.delivery.security.SessionIdentity
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@Import(TestClockConfiguration::class)
class OidcHttpTest : ApiIntegrationTest() {
    companion object {
        private val provider = OidcTestProvider()

        @DynamicPropertySource
        @JvmStatic
        fun oidc(registry: DynamicPropertyRegistry) {
            registry.add("spring.session.jdbc.flush-mode") { "immediate" }
            registry.add("HRIS_OIDC_ENABLED") { true }
            registry.add("HRIS_OIDC_ISSUER") { provider.issuer }
            registry.add("HRIS_OIDC_CLIENT_ID") { "fixture-client" }
            registry.add("HRIS_OIDC_CLIENT_SECRET") { "fixture-secret" }
            registry.add("HRIS_OIDC_AUTHORIZATION_URI") { provider.issuer + "/authorize" }
            registry.add("HRIS_OIDC_TOKEN_URI") { provider.issuer + "/token" }
            registry.add("HRIS_OIDC_JWK_SET_URI") { provider.issuer + "/jwks" }
            registry.add("HRIS_PUBLIC_URL") { "http://127.0.0.1" }
            registry.add("HRIS_OIDC_ALLOW_LOOPBACK_HTTP") { true }
        }

        @AfterAll
        @JvmStatic
        fun stopIssuer() {
            provider.close()
        }
    }

    @Autowired private lateinit var clock: MutableTestClock
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var operations: OperationRepository

    @BeforeEach
    fun resetTime() {
        clock.set(Instant.now())
        provider.clear()
    }

    private data class Fixture(
        val admin: HttpClient,
        val csrf: String,
        val account: UUID,
        val identity: UUID,
        val subject: String,
    )

    private fun fixture(link: Boolean = true): Fixture {
        val admin = client()
        val csrf = login(admin)
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'SSO employee',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        val f = Fixture(admin, csrf, account, UUID.randomUUID(), UUID.randomUUID().toString())
        if (link) {
            val response = save(f)
            assertEquals(200, response.statusCode(), response.body())
        }
        return f
    }

    private fun save(
        f: Fixture,
        key: UUID = UUID.randomUUID(),
        version: Long? = null,
        active: Boolean = true,
        account: UUID = f.account,
        id: UUID = f.identity,
        subject: String = f.subject,
    ): HttpResponse<String> =
        command(
            f.admin,
            "/api/v1/identity/accounts/$account/oidc/$id",
            json.writeValueAsString(
                mapOf(
                    "issuer" to provider.issuer,
                    "subject" to subject,
                    "active" to active,
                    "expectedVersion" to version,
                    "reason" to "Verified IdP registration",
                )
            ),
            f.csrf,
            key,
            "PUT",
        )

    private fun authorization(client: HttpClient): URI {
        val response = get(client, "/oauth2/authorization/company")
        assertEquals(302, response.statusCode(), response.body())
        return URI(response.headers().firstValue("Location").orElseThrow())
    }

    private fun callback(
        client: HttpClient,
        authorization: URI,
        subject: String,
        invalid: String? = null,
    ): HttpResponse<String> {
        val state =
            OidcTestProvider.parameters(requireNotNull(authorization.rawQuery)).getValue("state")
        val code = provider.issue(authorization, subject, invalid)
        return get(client, "/login/oauth2/code/company?state=$state&code=$code")
    }

    @Test
    fun verifiedOidcUsesPkceAndPersistsOnlyTheMinimalApplicationSession() {
        val f = fixture()
        val browser = client()
        assertEquals(200, get(browser, "/api/v1/auth/providers").statusCode())
        val oldCsrf =
            json.readTree(get(browser, "/api/v1/auth/csrf").body()).get("token").asString()
        val cookies = browser.cookieHandler().orElseThrow() as java.net.CookieManager
        val oldSession = cookies.cookieStore.cookies.first { it.name == "SESSION" }.value
        val start = authorization(browser)
        assertEquals(
            "http://127.0.0.1/login/oauth2/code/company",
            OidcTestProvider.parameters(start.rawQuery)["redirect_uri"],
        )
        val result = callback(browser, start, f.subject)
        assertEquals(302, result.statusCode(), result.body())
        assertEquals("/auth/complete", result.headers().firstValue("Location").orElseThrow())
        assertNotEquals(
            oldSession,
            cookies.cookieStore.cookies.first { it.name == "SESSION" }.value,
        )
        assertEquals(403, post(browser, "/api/v1/auth/logout", "{}", oldCsrf).statusCode())
        val me = get(browser, "/api/v1/me")
        assertEquals(200, me.statusCode(), me.body())
        assertEquals(
            f.account.toString(),
            json.readTree(me.body()).get("account").get("id").asString(),
        )
        assertTrue(json.readTree(me.body()).get("permissions").isEmpty)
        val sessions =
            database()
                .queryForList(
                    "select attribute_bytes from spring_session_attributes where attribute_name='SPRING_SECURITY_CONTEXT'",
                    ByteArray::class.java,
                )
        assertTrue(sessions.isNotEmpty())
        sessions.forEach { bytes ->
            val context =
                ObjectInputStream(ByteArrayInputStream(bytes)).use {
                    it.readObject() as SecurityContext
                }
            val principal = requireNotNull(context.authentication).principal
            assertTrue(principal is SessionIdentity)
            if ((principal as SessionIdentity).accountId == f.account)
                assertNull(principal.mfaVerifiedAt)
            assertFalse(
                String(requireNotNull(bytes), Charsets.ISO_8859_1).contains("fictional-provider")
            )
        }
        val replay = callback(browser, start, f.subject)
        assertEquals("/login?sso=failed", replay.headers().firstValue("Location").orElseThrow())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='identity.oidc_verified'",
                    Int::class.java,
                    f.account,
                ),
        )
    }

    @Test
    fun invalidClaimsAndUnlinkedSubjectsCannotUseMatchingEmailOrProviderRoles() {
        val f = fixture()
        for (invalid in listOf("signature", "issuer", "audience", "expiry", "nonce", null)) {
            val browser = client()
            val start = authorization(browser)
            val response =
                callback(
                    browser,
                    start,
                    if (invalid == null) "unlinked-subject" else f.subject,
                    invalid,
                )
            assertEquals(
                "/login?sso=failed",
                response.headers().firstValue("Location").orElseThrow(),
                invalid,
            )
            assertEquals(401, get(browser, "/api/v1/me").statusCode(), invalid)
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='identity.oidc_verified'",
                    Int::class.java,
                    f.account,
                ),
        )
    }

    @Test
    fun identityChangesAreVersionedReplayableAndRevokeExistingSessions() {
        val f = fixture(false)
        val old = client()
        login(old, "${f.account}@example.test")
        val key = UUID.randomUUID()
        val saved = save(f, key)
        assertEquals(200, saved.statusCode(), saved.body())
        assertEquals(saved.body(), save(f, key).body())
        assertEquals(401, get(old, "/api/v1/me").statusCode())
        assertEquals(409, save(f, key, active = false).statusCode())
        val browser = client()
        assertEquals(
            "/auth/complete",
            callback(browser, authorization(browser), f.subject)
                .headers()
                .firstValue("Location")
                .orElseThrow(),
        )
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val requests =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        check(go.await(5, TimeUnit.SECONDS))
                        save(f, version = 0, active = false).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            assertEquals(listOf(200, 409), requests.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(401, get(browser, "/api/v1/me").statusCode())
        val foreign = fixture(false)
        assertEquals(409, save(f, account = foreign.account, id = UUID.randomUUID()).statusCode())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    f.account,
                ),
        )
    }

    @Test
    fun companyAdministrationDoesNotGrantCredentialAdministration() {
        val f = fixture(false)
        val company = UUID.randomUUID()
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                "S${company.toString().take(8)}",
                "SSO permission fixture",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                f.account,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'identity.manage')",
                company,
                f.account,
            )
        val scoped = client()
        val csrf = login(scoped, "${f.account}@example.test")
        val attempt = save(f.copy(admin = scoped, csrf = csrf))
        assertEquals(403, attempt.statusCode(), attempt.body())
        assertEquals(
            "platform_administrator_required",
            json.readTree(attempt.body()).get("code").asString(),
        )
        val global = Actor(UUID.randomUUID(), null, emptySet(), clock.instant(), UUID.randomUUID())
        val receiptKey = OperationKey("fixture.global", UUID.randomUUID(), listOf("first"))
        assertTrue(
            transactions.run(global) {
                operations.record(global, receiptKey, MutationReceipt(UUID.randomUUID(), 0))
            } is Result.Success
        )
        val companyScope = global.copy(companyId = company)
        assertEquals(
            Result.Success<MutationReceipt?>(null),
            transactions.run(companyScope) { operations.lockAndReplay(companyScope, receiptKey) },
        )
        val other = global.copy(accountId = UUID.randomUUID())
        assertEquals(
            Result.Success<MutationReceipt?>(null),
            transactions.run(other) { operations.lockAndReplay(other, receiptKey) },
        )
    }

    @Test
    fun failedAuditRollsBackTheBindingCredentialVersionAndGlobalReceipt() {
        val f = fixture(false)
        val key = UUID.randomUUID()
        database()
            .execute(
                """CREATE FUNCTION reject_oidc_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
            IF NEW.action='identity.oidc_identity_changed' THEN RAISE EXCEPTION 'fixture failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$"""
            )
        database()
            .execute(
                "CREATE TRIGGER reject_oidc_audit BEFORE INSERT ON audit_entries FOR EACH ROW EXECUTE FUNCTION reject_oidc_audit()"
            )
        try {
            assertNotEquals(200, save(f, key).statusCode())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from oidc_identities where account_id=?",
                        Int::class.java,
                        f.account,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select security_version from accounts where id=?",
                        Long::class.java,
                        f.account,
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
            database().execute("DROP TRIGGER reject_oidc_audit ON audit_entries")
            database().execute("DROP FUNCTION reject_oidc_audit()")
        }
        assertEquals(200, save(f, key).statusCode())
    }

    @Test
    fun expiredAuthorizationIsRejectedBeforeTheTokenExchange() {
        val f = fixture()
        val browser = client()
        val start = authorization(browser)
        val previous = provider.exchanges.get()
        clock.set(clock.instant().plusSeconds(301))
        val response = callback(browser, start, f.subject)
        assertEquals("/login?sso=failed", response.headers().firstValue("Location").orElseThrow())
        assertEquals(previous, provider.exchanges.get())
        assertEquals(401, get(browser, "/api/v1/me").statusCode())
    }

    @Test
    fun aCallbackWithTheWrongStateCannotConsumeTheAuthorizationRequest() {
        val f = fixture()
        val browser = client()
        val start = authorization(browser)
        val code = provider.issue(start, f.subject)
        val before = provider.exchanges.get()
        val rejected = get(browser, "/login/oauth2/code/company?state=unrelated&code=$code")
        assertEquals("/login?sso=failed", rejected.headers().firstValue("Location").orElseThrow())
        assertEquals(before, provider.exchanges.get())
        val state = OidcTestProvider.parameters(start.rawQuery).getValue("state")
        val accepted = get(browser, "/login/oauth2/code/company?state=$state&code=$code")
        assertEquals("/auth/complete", accepted.headers().firstValue("Location").orElseThrow())
    }
}

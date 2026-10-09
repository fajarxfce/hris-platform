package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@Import(IdentityAdministrationProbeConfiguration::class)
class IdentityAdministrationAccessHttpTest : CredentialApiFixture() {
    companion object {
        private val provider = OidcTestProvider()

        @JvmStatic
        @DynamicPropertySource
        fun oidc(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_OIDC_ENABLED") { true }
            registry.add("HRIS_OIDC_ISSUER") { provider.issuer }
            registry.add("HRIS_OIDC_CLIENT_ID") { "fixture-client" }
            registry.add("HRIS_OIDC_CLIENT_SECRET") { "fixture-secret" }
            registry.add("HRIS_OIDC_AUTHORIZATION_URI") { provider.issuer + "/authorize" }
            registry.add("HRIS_OIDC_TOKEN_URI") { provider.issuer + "/token" }
            registry.add("HRIS_OIDC_JWK_SET_URI") { provider.issuer + "/jwks" }
            registry.add("HRIS_OIDC_ALLOW_LOOPBACK_HTTP") { true }
        }

        @JvmStatic
        @AfterAll
        fun stopIssuer() {
            provider.close()
        }
    }

    @Autowired private lateinit var probe: AccountLockProbe
    @Autowired private lateinit var directoryProbe: AccountDirectoryProbe

    private data class Administrator(val id: UUID, val browser: HttpClient, val csrf: String)

    private data class Change(val path: String, val body: String, val method: String = "PUT")

    private fun administrator(): Administrator {
        val id = localAccount()
        database()
            .update(
                "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
                id,
            )
        val browser = client()
        return Administrator(id, browser, login(browser, "$id@example.test"))
    }

    private fun change(mode: String, target: UUID): Change =
        when (mode) {
            "access" ->
                Change(
                    "/api/v1/identity/accounts/$target/access",
                    json.writeValueAsString(
                        mapOf(
                            "expectedVersion" to 0,
                            "active" to false,
                            "platformPermissions" to emptySet<String>(),
                            "reason" to "Reviewed access",
                        )
                    ),
                )
            "invite" ->
                Change(
                    "/api/v1/identity/invitations",
                    json.writeValueAsString(
                        mapOf(
                            "id" to target,
                            "email" to "$target@example.test",
                            "displayName" to "Invited employee",
                            "reason" to "Approved invitation",
                        )
                    ),
                    "POST",
                )
            "oidc" ->
                Change(
                    "/api/v1/identity/accounts/$target/oidc/${UUID.randomUUID()}",
                    json.writeValueAsString(
                        mapOf(
                            "issuer" to provider.issuer,
                            "subject" to UUID.randomUUID().toString(),
                            "active" to true,
                            "reason" to "Verified provider registration",
                        )
                    ),
                )
            else -> error("Unknown fixture operation")
        }

    private fun send(f: Administrator, change: Change, key: UUID = UUID.randomUUID()) =
        command(f.browser, change.path, change.body, f.csrf, key, change.method)

    private fun waiting(
        account: UUID,
        action: () -> HttpResponse<String>,
        whilePending: () -> Unit,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(account)
        probe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { action() }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                whilePending()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                probe.current.set(null)
            }
        }
    }

    @Test
    fun waitingAdministrativeCommandsAndTheirReceiptsRequireFreshAuthentication() {
        for (mode in listOf("access", "invite", "oidc")) {
            var f = administrator()
            val target = if (mode == "invite") UUID.randomUUID() else localAccount()
            val request = change(mode, target)
            val key = UUID.randomUUID()
            var original: String? = null
            for (phase in listOf("initial", "replay")) {
                val denied =
                    waiting(f.id, { send(f, request, key) }) {
                        clock.set(clock.instant().plusSeconds(601))
                    }
                assertEquals(403, denied.statusCode(), "$mode $phase ${denied.body()}")
                assertEquals(
                    "recent_authentication_required",
                    json.readTree(denied.body())["code"].asString(),
                )
                if (phase == "initial") {
                    val count =
                        database()
                            .queryForObject(
                                "select count(*) from audit_entries where resource_id=?",
                                Int::class.java,
                                target,
                            )
                    assertEquals(0, count)
                    if (mode == "invite")
                        assertEquals(
                            0,
                            database()
                                .queryForObject(
                                    "select count(*) from accounts where id=?",
                                    Int::class.java,
                                    target,
                                ),
                        )
                    if (mode == "oidc")
                        assertEquals(
                            0,
                            database()
                                .queryForObject(
                                    "select count(*) from oidc_identities where account_id=?",
                                    Int::class.java,
                                    target,
                                ),
                        )
                    if (mode == "access")
                        assertEquals(
                            0L,
                            database()
                                .queryForObject(
                                    "select version from accounts where id=?",
                                    Long::class.java,
                                    target,
                                ),
                        )
                }
                f = f.copy(csrf = login(f.browser, "${f.id}@example.test"))
                val saved = send(f, request, key)
                assertEquals(200, saved.statusCode(), "$mode $phase ${saved.body()}")
                if (original == null) original = saved.body()
                else assertEquals(original, saved.body())
            }
        }
    }

    @Test
    fun pendingGlobalRevocationBlocksMutationAndOldCredentialsCannotReadReceipts() {
        for (mode in listOf("access", "invite", "oidc")) {
            var f = administrator()
            val target = if (mode == "invite") UUID.randomUUID() else localAccount()
            val request = change(mode, target)
            val key = UUID.randomUUID()
            val denied =
                waiting(f.id, { send(f, request, key) }) {
                    database().update("delete from platform_permissions where account_id=?", f.id)
                }
            assertEquals(if (mode == "oidc") 403 else 401, denied.statusCode(), denied.body())
            database()
                .update(
                    "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
                    f.id,
                )
            val saved = send(f, request, key)
            assertEquals(200, saved.statusCode(), saved.body())
            val replayDenied =
                waiting(f.id, { send(f, request, key) }) {
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            f.id,
                        )
                }
            assertEquals(401, replayDenied.statusCode(), replayDenied.body())
            f = f.copy(csrf = login(f.browser, "${f.id}@example.test"))
            val replay = send(f, request, key)
            assertEquals(200, replay.statusCode(), replay.body())
            assertEquals(saved.body(), replay.body())
        }
    }

    @Test
    fun accountMetadataReadsRejectCredentialsRevokedWhileWaiting() {
        for (mode in listOf("directory", "oidcOther", "oidcOwn", "sessions", "me")) {
            val f = administrator()
            val path =
                when (mode) {
                    "directory" -> "/api/v1/identity/accounts"
                    "oidcOther" -> "/api/v1/identity/accounts/${localAccount()}/oidc"
                    "oidcOwn" -> "/api/v1/identity/accounts/${f.id}/oidc"
                    "sessions" -> "/api/v1/auth/native/sessions"
                    else -> "/api/v1/me"
                }
            val denied =
                waiting(f.id, { get(f.browser, path) }) {
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            f.id,
                        )
                }
            assertEquals(401, denied.statusCode(), "$mode ${denied.body()}")
            assertEquals("session_revoked", json.readTree(denied.body())["code"].asString())
        }
    }

    @Test
    fun onlyCurrentAdministratorsCanReadOtherAccountsButOwnersRetainTheirOwnMetadata() {
        for (mode in listOf("directory", "oidc")) {
            val f = administrator()
            val path =
                if (mode == "directory") "/api/v1/identity/accounts"
                else "/api/v1/identity/accounts/${localAccount()}/oidc"
            val denied =
                waiting(f.id, { get(f.browser, path) }) {
                    database().update("delete from platform_permissions where account_id=?", f.id)
                }
            assertEquals(403, denied.statusCode(), denied.body())
            for (owned in
                listOf(
                    "/api/v1/identity/accounts/${f.id}/oidc",
                    "/api/v1/auth/native/sessions",
                    "/api/v1/me",
                )) {
                val allowed = get(f.browser, owned)
                assertEquals(200, allowed.statusCode(), "$owned ${allowed.body()}")
            }
        }
    }

    @Test
    fun selfOidcChangesRevokeTheOriginSessionAndFreshAuthenticationCanReplay() {
        var f = administrator()
        val request = change("oidc", f.id)
        val key = UUID.randomUUID()
        val saved = send(f, request, key)
        assertEquals(200, saved.statusCode(), saved.body())
        assertEquals(401, send(f, request, key).statusCode())
        f = f.copy(csrf = login(f.browser, "${f.id}@example.test"))
        val replay = send(f, request, key)
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(saved.body(), replay.body())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from oidc_identities where account_id=?",
                    Int::class.java,
                    f.id,
                ),
        )
    }

    @Test
    fun crossedOidcAdministrationHasOrderedAccountGuardsAndCannotUseRevokedCredentials() {
        val first = administrator()
        val second = administrator()
        val firstChange = change("oidc", second.id)
        val secondChange = change("oidc", first.id)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val requests =
                listOf(first to firstChange, second to secondChange).map { (f, request) ->
                    pool.submit<HttpResponse<String>> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        send(f, request)
                    }
                }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                assertEquals(
                    listOf(200, 401),
                    requests.map { it.get(10, TimeUnit.SECONDS).statusCode() }.sorted(),
                )
            } finally {
                start.countDown()
            }
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from oidc_identities where account_id in (?,?)",
                    Int::class.java,
                    first.id,
                    second.id,
                ),
        )
    }

    @Test
    fun accountDirectoryKeepsObservedVersionAndPermissionsInOneSnapshot() {
        val reader = administrator()
        val writer = administrator()
        val target = localAccount()
        database()
            .update(
                "insert into platform_permissions(account_id,permission) values(?,'companies.create')",
                target,
            )
        val path = "/api/v1/identity/accounts?query=$target&limit=1"
        val barrier = AccountDirectoryProbe.Barrier(target.toString())
        directoryProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(reader.browser, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val changed = send(writer, change("access", target))
                assertEquals(200, changed.statusCode(), changed.body())
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, response.statusCode(), response.body())
                val old = json.readTree(response.body())["items"][0]
                assertEquals(0, old["version"].asLong())
                assertTrue(old["active"].asBoolean())
                assertEquals("companies.create", old["platformPermissions"][0].asString())
                val fresh = json.readTree(get(reader.browser, path).body())["items"][0]
                assertEquals(1, fresh["version"].asLong())
                assertFalse(fresh["active"].asBoolean())
                assertTrue(fresh["platformPermissions"].isEmpty)
            } finally {
                barrier.release.countDown()
                directoryProbe.current.set(null)
            }
        }
    }
}

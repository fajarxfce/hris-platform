package dev.fajar.hris

import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class MembershipAssuranceHttpTest : MfaApiFixture() {
    @Autowired private lateinit var probe: AccountLockProbe
    @Autowired private lateinit var security: IdentitySecurityPolicy

    @ParameterizedTest
    @ValueSource(strings = ["members", "roles", "grant"])
    fun memberAndRoleReadsRecheckMfaAfterWaitingAndRecoverWithAFreshProof(mode: String) {
        val enrolled = enroll(fixture())
        val f = enrolled.fixture
        val created =
            command(
                f.client,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "M${UUID.randomUUID().toString().take(8)}",
                        "name" to "Member read assurance",
                        "timezone" to "UTC",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val company = UUID.fromString(json.readTree(created.body())["id"].asString())
        val path =
            "/api/v1/companies/$company/" +
                when (mode) {
                    "members" -> "members"
                    "roles" -> "role-templates"
                    else -> "members/${f.account}"
                }
        val initial = get(f.client, path)
        assertEquals(200, initial.statusCode(), initial.body())
        val barrier = AccountLockProbe.Barrier(f.account)
        probe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.client, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plus(security.maximumMfaAge).plusSeconds(1))
                barrier.release.countDown()
                val denied = pending.get(10, TimeUnit.SECONDS)
                assertEquals(403, denied.statusCode(), "$mode ${denied.body()}")
                assertEquals("mfa_required", json.readTree(denied.body())["code"].asString())
                assertFalse(denied.body().contains(f.email))
            } finally {
                barrier.release.countDown()
                probe.current.set(null)
            }
        }
        val verified = verify(f, totp(enrolled.secret, clock.instant()))
        assertEquals(200, verified.statusCode(), verified.body())
        val recovered = get(f.client, path)
        assertEquals(200, recovered.statusCode(), recovered.body())
    }

    @Test
    fun expiredMfaDuringPendingRoleAndMembershipChangesAlsoBlocksReceipts() {
        for (mode in listOf("role", "member")) {
            val enrolled = enroll(fixture())
            var f = enrolled.fixture
            val created =
                command(
                    f.client,
                    "/api/v1/companies",
                    json.writeValueAsString(
                        mapOf(
                            "code" to "A${UUID.randomUUID().toString().take(8)}",
                            "name" to "Access fixture",
                            "timezone" to "UTC",
                        )
                    ),
                    f.csrf,
                    UUID.randomUUID(),
                )
            assertEquals(200, created.statusCode(), created.body())
            val company = UUID.fromString(json.readTree(created.body())["id"].asString())
            val id = UUID.randomUUID()
            if (mode == "member")
                database()
                    .update(
                        "insert into accounts(id,email,display_name,password_hash) select ?,?,'Member fixture',password_hash from accounts where id=?",
                        id,
                        "$id@example.test",
                        f.account,
                    )
            val path =
                "/api/v1/companies/$company/" +
                    if (mode == "role") "role-templates/$id" else "members/$id"
            val body =
                json.writeValueAsString(
                    if (mode == "role")
                        mapOf(
                            "code" to "REVIEWER",
                            "name" to "Reviewer",
                            "permissions" to setOf("company.read"),
                            "reason" to "Reviewed role",
                        )
                    else
                        mapOf(
                            "permissions" to setOf("company.read"),
                            "reason" to "Approved membership",
                        )
                )
            val key = UUID.randomUUID()
            for (phase in listOf("initial", "replay")) {
                val barrier = AccountLockProbe.Barrier(f.account)
                probe.current.set(barrier)
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending =
                        pool.submit<HttpResponse<String>> {
                            command(f.client, path, body, f.csrf, key, "PUT")
                        }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        clock.set(clock.instant().plusSeconds(601))
                        barrier.release.countDown()
                        val denied = pending.get(10, TimeUnit.SECONDS)
                        assertEquals(403, denied.statusCode(), "$mode $phase ${denied.body()}")
                        assertEquals(
                            "recent_authentication_required",
                            json.readTree(denied.body())["code"].asString(),
                        )
                    } finally {
                        barrier.release.countDown()
                        probe.current.set(null)
                    }
                }
                val verified = verify(f, totp(enrolled.secret, clock.instant()))
                assertEquals(200, verified.statusCode(), verified.body())
                f = f.copy(csrf = csrf(f.client))
                val saved = command(f.client, path, body, f.csrf, key, "PUT")
                assertEquals(200, saved.statusCode(), "$mode $phase ${saved.body()}")
            }
        }
    }
}

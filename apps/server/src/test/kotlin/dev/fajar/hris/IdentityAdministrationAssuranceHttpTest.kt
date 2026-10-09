package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(IdentityAdministrationProbeConfiguration::class)
class IdentityAdministrationAssuranceHttpTest : MfaApiFixture() {
    @Autowired private lateinit var probe: AccountLockProbe

    @Test
    fun pendingAccountAccessAndItsReceiptRequireCurrentMfa() {
        val enrolled = enroll(fixture())
        var f = enrolled.fixture
        val target = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Target account',password_hash from accounts where id=?",
                target,
                "$target@example.test",
                f.account,
            )
        val path = "/api/v1/identity/accounts/$target/access"
        val body =
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to 0,
                    "active" to false,
                    "platformPermissions" to emptySet<String>(),
                    "reason" to "Reviewed access",
                )
            )
        val key = UUID.randomUUID()
        var original: String? = null
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
                    assertEquals(403, denied.statusCode(), "$phase ${denied.body()}")
                    assertEquals(
                        "recent_authentication_required",
                        json.readTree(denied.body())["code"].asString(),
                    )
                } finally {
                    barrier.release.countDown()
                    probe.current.set(null)
                }
            }
            val proof = verify(f, totp(enrolled.secret, clock.instant()))
            assertEquals(200, proof.statusCode(), proof.body())
            f = f.copy(csrf = csrf(f.client))
            val saved = command(f.client, path, body, f.csrf, key, "PUT")
            assertEquals(200, saved.statusCode(), saved.body())
            if (original == null) original = saved.body() else assertEquals(original, saved.body())
        }
    }
}

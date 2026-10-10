package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.usecases.RevokeNativeSession
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(NativePushRegistrationProbeConfiguration::class)
class NativeSessionRevocationHttpTest : MfaApiFixture() {
    @Autowired private lateinit var revoke: RevokeNativeSession
    @Autowired private lateinit var probe: NativePushRegistrationProbe

    @AfterEach
    fun releaseProbe() {
        probe.current.getAndSet(null)?.release?.countDown()
    }

    @ParameterizedTest
    @ValueSource(strings = ["account", "credential"])
    fun pendingRevocationCannotUseAnAccountOrCredentialThatWasWithdrawn(change: String) {
        val f = enroll(fixture()).fixture
        val response =
            command(
                f.client,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Revocation fixture"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        val session = UUID.fromString(json.readTree(response.body())["sessionId"].asString())
        val version =
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    f.account,
                )
        val actor =
            Actor(
                f.account,
                null,
                emptySet(),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = version,
            )
        val barrier = NativePushRegistrationProbe.Barrier(f.account)
        probe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<Result<Unit>> { revoke.execute(actor, session) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                if (change == "account")
                    database().update("update accounts set active=false where id=?", f.account)
                else
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            f.account,
                        )
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals("session_revoked", (result as? Result.Failed)?.failure?.code)
                assertNull(
                    database()
                        .queryForMap("select revoked_at from native_sessions where id=?", session)[
                            "revoked_at"]
                )
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from audit_entries where resource_id=? and action='identity.native_session_revoked'",
                            Int::class.java,
                            session,
                        ),
                )
            } finally {
                barrier.release.countDown()
            }
        }
    }
}

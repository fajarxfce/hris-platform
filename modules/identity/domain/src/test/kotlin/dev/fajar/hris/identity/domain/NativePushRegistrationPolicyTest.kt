package dev.fajar.hris.identity.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativePushRegistrationPolicyTest {
    @Test
    fun liveAccountCredentialsSessionVersionAndAccessExpiryAllConstrainRegistration() {
        val now = Instant.parse("2026-10-01T00:00:00Z")
        val id = UUID.randomUUID()
        val actor = Actor(id, null, emptySet(), now, UUID.randomUUID(), credentialVersion = 3)
        val account = Account(id, "fixture@example.test", "Fixture", true, false, 0, 3)
        val session =
            NativeSession(
                UUID.randomUUID(),
                id,
                "Test",
                now,
                now,
                null,
                3,
                now.plusSeconds(3600),
                now.plusSeconds(600),
                now,
                null,
                2,
                UUID.randomUUID(),
                null,
            )
        assertEquals(Result.Success(Unit), validateNativePushActor(actor, account, session, 2, now))
        val invalid =
            listOf(
                validateNativePushActor(actor, account.copy(active = false), session, 2, now),
                validateNativePushActor(actor, account.copy(securityVersion = 4), session, 2, now),
                validateNativePushActor(
                    actor,
                    account,
                    session.copy(accountId = UUID.randomUUID()),
                    2,
                    now,
                ),
                validateNativePushActor(actor, account, session.copy(revokedAt = now), 2, now),
                validateNativePushActor(actor, account, session, 1, now),
                validateNativePushActor(actor, account, session, 2, now.plusSeconds(600)),
                validateNativePushActor(actor, account, session, 2, now.minusSeconds(1)),
                validateNativePushActor(
                    actor.copy(credentialVersion = null),
                    account,
                    session,
                    2,
                    now,
                ),
            )
        assertTrue(
            invalid.all { it is Result.Failed && it.failure.code == "native_session_invalid" }
        )
    }
}

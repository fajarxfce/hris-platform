package dev.fajar.hris.identity.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CredentialPolicyTest {
    @Test
    fun retriesAreBoundedByClassificationAttemptAndCredentialExpiry() {
        val now = Instant.parse("2026-10-08T00:00:00Z")
        val until = now.plusSeconds(1800)
        val unavailable = Failure(FailureKind.UNAVAILABLE, "mail_temporarily_unavailable")
        assertEquals(
            now.plusSeconds(5),
            identityMailOutcome(1, unavailable, now, until, 8).availableAt,
        )
        assertEquals(
            now.plusSeconds(300),
            identityMailOutcome(7, unavailable, now, until, 8).availableAt,
        )
        assertEquals(
            IdentityMailState.FAILED,
            identityMailOutcome(8, unavailable, now, until, 8).state,
        )
        assertEquals(
            IdentityMailState.FAILED,
            identityMailOutcome(1, unavailable, now, now.plusSeconds(5), 8).state,
        )
        assertTrue(
            identityMailOutcome(
                    1,
                    Failure(FailureKind.VALIDATION, "mail_recipient_rejected"),
                    now,
                    until,
                    8,
                )
                .discardToken
        )
        assertEquals(IdentityMailState.SENT, identityMailOutcome(1, null, now, until, 8).state)
    }

    @Test
    fun challengeIsBoundToKindAccountVersionAndAnExclusiveExpiry() {
        val now = Instant.parse("2026-10-08T00:00:00Z")
        val account =
            CredentialAccount(
                UUID.randomUUID(),
                "employee@example.test",
                "Employee",
                false,
                true,
                false,
                0,
                3,
            )
        val challenge =
            CredentialChallenge(
                UUID.randomUUID(),
                account.id,
                CredentialChallengeKind.INVITATION,
                3,
                UUID.randomUUID(),
                now,
                now.plusSeconds(30),
                null,
                null,
            )
        assertTrue(credentialChallengeUsable(challenge, account, now))
        assertFalse(credentialChallengeUsable(challenge, account, now.plusSeconds(30)))
        assertFalse(credentialChallengeUsable(challenge, account.copy(securityVersion = 4), now))
        assertFalse(credentialChallengeUsable(challenge, account.copy(active = true), now))
        assertFalse(
            credentialChallengeUsable(
                challenge.copy(kind = CredentialChallengeKind.PASSWORD_RECOVERY),
                account,
                now,
            )
        )
        val mail =
            credentialMail(
                challenge,
                account,
                "fixture-token",
                CredentialChallengePolicy(true, "https://hris.example.test"),
            )
        assertTrue(
            mail.text.contains(
                "\n\nhttps://hris.example.test/auth/accept-invitation#token=fixture-token\n\n"
            )
        )
        assertFalse(mail.toString().contains("fixture-token"))
        assertFalse(
            IssuedCredentialLink(challenge, "fixture-token").toString().contains("fixture-token")
        )
    }

    @Test
    fun invitationValidationRejectsHeaderGroupsAndMalformedAddresses() {
        assertTrue(validAccountEmail("first.last+hr@example.test"))
        for (email in
            listOf(
                "x@example.test\r\nBcc:y@example.test",
                "group:x@example.test;",
                ".x@example.test",
                "x..y@example.test",
                "x@example..test",
                "x@-example.test",
                "x@example.test,y@example.test",
            )) assertFalse(validAccountEmail(email), email)
        assertTrue(
            validateInvitation("x@example.test", "Employee", "Provisioning", null) is Result.Success
        )
        assertTrue(
            validateInvitation("x@example.test", "Employee\nInjected", "Provisioning", 0)
                is Result.Failed
        )
    }
}

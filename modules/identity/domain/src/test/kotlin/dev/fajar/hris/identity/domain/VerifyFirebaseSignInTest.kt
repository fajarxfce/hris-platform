package dev.fajar.hris.identity.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.identity.domain.usecases.VerifyFirebaseSignIn
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VerifyFirebaseSignInTest {
    private val correlation = UUID.randomUUID()
    private val token = "signed-token-".repeat(8)

    @Test
    fun originAdmissionPrecedesSignatureWorkAndVerifiedSubjectsReceiveTheirOwnLimit() {
        val f = Fixture()
        assertEquals(
            Result.Success(f.identity),
            f.useCase().execute(token, "192.0.2.1", correlation),
        )
        assertEquals(1, f.verifications)
        assertEquals(
            listOf(
                AuthenticationAttemptKind.FIREBASE_VERIFICATION,
                AuthenticationAttemptKind.FIREBASE_SIGN_IN,
            ),
            f.kinds,
        )
        assertEquals(listOf("192.0.2.1", "cmp-fajar:uid"), f.subjects)
        assertEquals(listOf(100, 10), f.maximums)
        assertEquals(2, f.committed.size)
    }

    @Test
    fun deniedAttemptsCommitAccountingAndNeverContinuePastTheirGate() {
        for (kind in
            listOf(
                AuthenticationAttemptKind.FIREBASE_VERIFICATION,
                AuthenticationAttemptKind.FIREBASE_SIGN_IN,
            )) {
            val f = Fixture()
            f.denied = kind
            val result = f.useCase().execute(token, "192.0.2.1", correlation)
            assertEquals(
                "sign_in_rate_limited",
                assertInstanceOf(Result.Failed::class.java, result).failure.code,
            )
            assertEquals(Result.Success(false), f.committed.last())
            assertEquals(
                if (kind == AuthenticationAttemptKind.FIREBASE_VERIFICATION) 0 else 1,
                f.verifications,
            )
        }
    }

    @Test
    fun disabledAndOversizedInputAvoidBothAccountingAndProviderIo() {
        val f = Fixture()
        assertInstanceOf(
            Result.Failed::class.java,
            f.useCase(false).execute(token, "192.0.2.1", correlation),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.useCase().execute("a".repeat(8193), "192.0.2.1", correlation),
        )
        assertInstanceOf(Result.Failed::class.java, f.useCase().execute(token, "", correlation))
        assertTrue(f.kinds.isEmpty())
        assertEquals(0, f.verifications)
    }

    @Test
    fun ageIsCheckedAfterProviderAndAccountingWorkWithoutIssuingAReplacementAuthenticationTime() {
        val f = Fixture()
        f.afterVerify = { f.now = f.now.plusSeconds(601) }
        assertEquals(
            "firebase_sign_in_failed",
            assertInstanceOf(
                    Result.Failed::class.java,
                    f.useCase().execute(token, "192.0.2.1", correlation),
                )
                .failure
                .code,
        )
        assertEquals(listOf(AuthenticationAttemptKind.FIREBASE_VERIFICATION), f.kinds)
        val second = Fixture()
        second.afterAccount = { second.now = second.now.plusSeconds(601) }
        assertInstanceOf(
            Result.Failed::class.java,
            second.useCase().execute(token, "192.0.2.1", correlation),
        )
        assertEquals(2, second.kinds.size)
    }

    @Test
    fun interruptedVerificationCannotPublishALateIdentityOrContinueAccounting() {
        val f = Fixture()
        f.afterVerify = { Thread.currentThread().interrupt() }
        try {
            assertThrows(CancellationException::class.java) {
                f.useCase().execute(token, "192.0.2.1", correlation)
            }
            assertEquals(listOf(AuthenticationAttemptKind.FIREBASE_VERIFICATION), f.kinds)
        } finally {
            Thread.interrupted()
        }
    }

    private class Fixture {
        var now = Instant.parse("2026-10-28T00:00:00Z")
        val identity =
            FirebaseIdentity(
                "https://securetoken.google.com/cmp-fajar",
                "uid",
                "cmp-fajar",
                now,
                now.plusSeconds(3600),
                now,
                "password",
            )
        var verifications = 0
        var afterVerify: () -> Unit = {}
        var afterAccount: () -> Unit = {}
        var denied: AuthenticationAttemptKind? = null
        val kinds = mutableListOf<AuthenticationAttemptKind>()
        val subjects = mutableListOf<String>()
        val maximums = mutableListOf<Int>()
        val committed = mutableListOf<Result<*>>()
        val clock =
            object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId): Clock = this

                override fun instant(): Instant = now
            }
        val transactions =
            object : TransactionRunner {
                override fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T> =
                    operation().also { committed += it }
            }
        val limits =
            object : AuthenticationRateLimitRepository {
                override fun purgeExpired(before: Instant, limit: Int): Result<Int> =
                    Result.Success(0)

                override fun takeAttempt(
                    email: String,
                    origin: String,
                    at: Instant,
                    policy: AuthenticationAttemptPolicy,
                    kind: AuthenticationAttemptKind,
                ): Result<Boolean> {
                    kinds += kind
                    subjects += email
                    maximums += policy.perAccount
                    if (kind == AuthenticationAttemptKind.FIREBASE_SIGN_IN) afterAccount()
                    return Result.Success(kind != denied)
                }
            }

        fun useCase(enabled: Boolean = true) =
            VerifyFirebaseSignIn(
                FirebaseIdentityRepository {
                    verifications++
                    afterVerify()
                    Result.Success(identity)
                },
                limits,
                transactions,
                FirebaseSignInPolicy(enabled, "cmp-fajar"),
                clock,
            )
    }
}

package dev.fajar.hris.identity.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.usecases.ResolveActor
import dev.fajar.hris.identity.domain.usecases.SignInWithPassword
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IdentityPolicyTest {
    private val now = Instant.parse("2026-10-08T00:00:00Z")
    private val account =
        Account(UUID.randomUUID(), "person@example.test", "Person", true, false, 0)
    private val transactions =
        object : TransactionRunner {
            override fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T> = operation()
        }

    private val limits =
        object : dev.fajar.hris.identity.domain.repositories.SignInLimitRepository {
            override fun takeAttempt(
                email: String,
                origin: String,
                at: Instant,
                policy: SignInAttemptPolicy,
            ) = Result.Success(true)

            override fun purgeExpired(before: Instant, limit: Int) = Result.Success(0)
        }

    @Test
    fun deniedBudgetPreventsPasswordWorkButIsCommittedAsAnAccountingOutcome() {
        val repo = IdentityFake(account, AccountAccess(account, emptySet(), true, true))
        val denied =
            object : dev.fajar.hris.identity.domain.repositories.SignInLimitRepository {
                override fun takeAttempt(
                    email: String,
                    origin: String,
                    at: Instant,
                    policy: SignInAttemptPolicy,
                ) = Result.Success(false)

                override fun purgeExpired(before: Instant, limit: Int) = Result.Success(0)
            }
        var transactionSucceeded = false
        val tx =
            object : TransactionRunner {
                override fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T> =
                    operation().also { transactionSucceeded = it is Result.Success }
            }
        val result =
            SignInWithPassword(
                    repo,
                    RecordingJournal(),
                    tx,
                    Clock.fixed(now, ZoneOffset.UTC),
                    denied,
                )
                .execute(account.email, "password", UUID.randomUUID(), "127.0.0.1")
        assertEquals(
            Result.Failed(Failure(FailureKind.RATE_LIMITED, "sign_in_rate_limited")),
            result,
        )
        assertTrue(transactionSucceeded)
        assertEquals(0, repo.verifications)
    }

    @Test
    fun disabledAccountCannotPublishAnAuthenticatedIdentity() {
        val repository =
            IdentityFake(
                account,
                AccountAccess(account.copy(active = false), emptySet(), true, true),
            )
        val journal = RecordingJournal()
        val result =
            SignInWithPassword(
                    repository,
                    journal,
                    transactions,
                    Clock.fixed(now, ZoneOffset.UTC),
                    limits,
                )
                .execute(account.email, "Example-password", UUID.randomUUID())
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "invalid_credentials")),
            result,
        )
        assertTrue(journal.changes.isEmpty())
    }

    @Test
    fun changedCredentialsWhileVerificationWasPendingRejectOldProof() {
        val repository =
            IdentityFake(account, AccountAccess(account.copy(version = 1), emptySet(), true, true))
        val journal = RecordingJournal()
        val result =
            SignInWithPassword(
                    repository,
                    journal,
                    transactions,
                    Clock.fixed(now, ZoneOffset.UTC),
                    limits,
                )
                .execute(account.email, "Example-password", UUID.randomUUID())
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "invalid_credentials")),
            result,
        )
        assertTrue(journal.changes.isEmpty())
    }

    @Test
    fun inactiveMembershipOrCompanyCannotGrantPermissions() {
        for (flags in listOf(false to true, true to false)) {
            val repository =
                IdentityFake(
                    account,
                    AccountAccess(account, setOf("payroll.read"), flags.first, flags.second),
                )
            val result =
                ResolveActor(
                        repository,
                        transactions,
                        Clock.fixed(now, ZoneOffset.UTC),
                        IdentitySecurityPolicy(enforceMfa = false),
                    )
                    .execute(account.id, UUID.randomUUID(), now, UUID.randomUUID())
            assertEquals(
                Result.Failed(Failure(FailureKind.FORBIDDEN, "company_access_denied")),
                result,
            )
        }
    }

    @Test
    fun activeMembershipReceivesOnlyItsCurrentExplicitPermissions() {
        val company = UUID.randomUUID()
        val repository =
            IdentityFake(account, AccountAccess(account, setOf("people.read"), true, true))
        val result =
            ResolveActor(
                    repository,
                    transactions,
                    Clock.fixed(now, ZoneOffset.UTC),
                    IdentitySecurityPolicy(enforceMfa = false),
                )
                .execute(account.id, company, now, UUID.randomUUID())
        assertInstanceOf(Result.Success::class.java, result)
        val actor = (result as Result.Success).value
        assertEquals(company, actor.companyId)
        assertEquals(setOf("people.read"), actor.permissions)
        assertInstanceOf(Result.Failed::class.java, actor.requirePermission("payroll.read"))
    }
}

private class RecordingJournal : ChangeJournalRepository {
    val changes = mutableListOf<ChangeRecord>()

    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
        changes += change
        return Result.Success(Unit)
    }
}

private class IdentityFake(private val credential: Account?, private val current: AccountAccess?) :
    IdentityRepository {
    var verifications = 0

    override fun verifyPassword(email: String, password: String): Result<Account?> {
        verifications++
        return Result.Success(credential)
    }

    override fun access(accountId: UUID, companyId: UUID?): Result<AccountAccess?> =
        Result.Success(current)

    override fun lockBootstrap(): Result<Unit> = error("Unexpected call")

    override fun hasAccounts(): Result<Boolean> = error("Unexpected call")

    override fun createAccount(
        id: UUID,
        email: String,
        displayName: String,
        password: String,
    ): Result<Account> = error("Unexpected call")

    override fun memberships(accountId: UUID): Result<List<CompanyMembership>> =
        error("Unexpected call")

    override fun grantPlatformPermissions(accountId: UUID, permissions: Set<String>): Result<Unit> =
        error("Unexpected call")

    override fun grantMembership(
        accountId: UUID,
        companyId: UUID,
        permissions: Set<String>,
    ): Result<Unit> = error("Unexpected call")
}

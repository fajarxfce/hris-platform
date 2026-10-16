package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.identity.domain.usecases.*
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class AccountNativeSessionCompletionTest : MfaApiFixture() {
    @Autowired private lateinit var sessions: NativeSessionRepository
    @Autowired private lateinit var accounts: AccountAdministrationRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val policy = NativeSessionPolicy(maximumSessions = 3)
    private val security =
        IdentitySecurityPolicy(
            maximumMfaAge = Duration.ofSeconds(4),
            recentAuthenticationAge = Duration.ofSeconds(2),
        )

    @BeforeEach
    fun resetClock() {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
    }

    private fun account(administrator: Boolean = false): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Owned session fixture',password_hash,'owned-fixture-only' from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        if (administrator)
            database()
                .update(
                    "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
                    id,
                )
        return id
    }

    private fun actor(id: UUID) =
        Actor(
            id,
            null,
            setOf("identity.manage"),
            clock.instant(),
            UUID.randomUUID(),
            mfaVerifiedAt = clock.instant(),
            credentialVersion = 0,
        )

    private fun session(
        account: UUID,
        credential: Long = 0,
        expired: Boolean = false,
        revoked: Boolean = false,
    ): NativeTokens {
        val now = clock.instant()
        val session =
            NativeSession(
                UUID.randomUUID(),
                account,
                "Owned device",
                now.minusSeconds(60),
                now,
                now,
                credential,
                if (expired) now else now.plusSeconds(86400),
                now.plusSeconds(600),
                now.minusSeconds(10),
                if (revoked) now else null,
                0,
                UUID.randomUUID(),
                now.plusSeconds(120),
            )
        val result =
            transactions.run(actor(account)) { sessions.create(session, now.plusSeconds(120)) }
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    private fun listing(
        source: NativeSessionRepository = sessions,
        access: IdentityRepository = identities,
        directory: AccountAdministrationRepository = accounts,
    ) = ListAccountNativeSessions(source, directory, access, transactions, clock, policy, security)

    private fun revoking(
        access: IdentityRepository = identities,
        changes: ChangeJournalRepository = journal,
    ) = RevokeAccountNativeSession(sessions, access, transactions, changes, clock, security)

    private fun audits(session: UUID): Int =
        requireNotNull(
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='identity.native_session_revoked'",
                    Int::class.java,
                    session,
                )
        )

    private fun assertRetained(session: UUID) {
        assertNull(
            database()
                .queryForMap("select revoked_at from native_sessions where id=?", session)[
                    "revoked_at"]
        )
        assertEquals(0, audits(session))
    }

    @Test
    fun readsAreBoundedAndExcludeExpiredRevokedAndObsoleteCredentials() {
        val author = actor(account(administrator = true))
        val target = account()
        val eligible = List(7) { session(target).sessionId }.toSet()
        session(target, credential = 1)
        session(target, expired = true)
        session(target, revoked = true)
        session(account())
        val read = listing().execute(author, target)
        assertTrue(read is Result.Success, read.toString())
        val rows = (read as Result.Success).value
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.id in eligible && it.accountId == target })
        assertEquals(rows.map { it.id }.sortedBy { it.toString() }, rows.map { it.id })
        assertEquals(
            "account_not_found",
            (listing().execute(author, UUID.randomUUID()) as? Result.Failed)?.failure?.code,
        )
        database().update("update accounts set active=false where id=?", target)
        assertEquals(3, (listing().execute(author, target) as Result.Success).value.size)
        assertTrue(revoking().execute(author, target, rows.first().id) is Result.Success)
    }

    @Test
    fun readsRecheckAssuranceAfterAcquisitionIncludingMissingTargets() {
        val author = actor(account(administrator = true))
        val target = account()
        session(target)
        val delayed =
            object : NativeSessionRepository by sessions {
                override fun list(
                    accountId: UUID,
                    at: Instant,
                    limit: Int,
                ): Result<List<NativeSession>> =
                    sessions.list(accountId, at, limit).also {
                        clock.set(clock.instant().plusSeconds(4))
                    }
            }
        assertEquals(
            "mfa_required",
            (listing(source = delayed).execute(author, target) as? Result.Failed)?.failure?.code,
        )
        val renewed = author.copy(mfaVerifiedAt = clock.instant())
        assertTrue(listing().execute(renewed, target) is Result.Success)
        val directory =
            object : AccountAdministrationRepository by accounts {
                override fun get(id: UUID): Result<ManagedAccount?> =
                    accounts.get(id).also { clock.set(clock.instant().plusSeconds(4)) }
            }
        assertEquals(
            "mfa_required",
            (listing(directory = directory).execute(renewed, UUID.randomUUID()) as? Result.Failed)
                ?.failure
                ?.code,
        )
    }

    @Test
    fun guardsAreOrderedAndScopeCannotElevateAnAdmittedActor() {
        val author = actor(account(administrator = true))
        val target = account()
        val token = session(target)
        val guards = mutableListOf<Pair<UUID, Boolean>>()
        val access =
            object : IdentityRepository by identities {
                override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> =
                    identities.lockAccount(accountId, shared).also { guards += accountId to shared }
            }
        assertTrue(listing(access = access).execute(author, target) is Result.Success)
        assertEquals(setOf(author.accountId, target).sorted().map { it to true }, guards)
        guards.clear()
        assertTrue(
            revoking(access = access).execute(author, target, token.sessionId) is Result.Success
        )
        assertEquals(setOf(author.accountId, target).sorted().map { it to false }, guards)
        guards.clear()
        val company = author.copy(companyId = UUID.randomUUID())
        val absentGrant = author.copy(permissions = emptySet())
        for (denied in listOf(company, absentGrant)) {
            assertEquals(
                "platform_administrator_required",
                (listing(access = access).execute(denied, target) as? Result.Failed)?.failure?.code,
            )
            assertEquals(
                "platform_administrator_required",
                (revoking(access = access).execute(denied, target, token.sessionId)
                        as? Result.Failed)
                    ?.failure
                    ?.code,
            )
        }
        assertTrue(guards.isEmpty())
        assertTrue(listing(access = access).execute(author, author.accountId) is Result.Success)
        assertEquals(listOf(author.accountId to true), guards)
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential", "inactive", "enrollment"])
    fun withdrawnAccessBeforeTheGuardCannotReadOrRevoke(change: String) {
        val author = actor(account(administrator = true))
        val target = account()
        val token = session(target)
        val changed = AtomicBoolean(false)
        val access =
            object : IdentityRepository by identities {
                override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> {
                    if (changed.compareAndSet(false, true)) {
                        val sql =
                            when (change) {
                                "permission" ->
                                    "delete from platform_permissions where account_id=?"
                                "credential" ->
                                    "update accounts set security_version=security_version+1 where id=?"
                                "inactive" -> "update accounts set active=false where id=?"
                                else -> "update accounts set mfa_secret_encrypted=null where id=?"
                            }
                        database().update(sql, author.accountId)
                    }
                    return identities.lockAccount(accountId, shared)
                }
            }
        val expected =
            when (change) {
                "permission" -> "platform_administrator_required"
                "enrollment" -> "mfa_setup_required"
                else -> "session_revoked"
            }
        assertEquals(
            expected,
            (listing(access = access).execute(author, target) as? Result.Failed)?.failure?.code,
        )
        assertEquals(
            expected,
            (revoking(access = access).execute(author, target, token.sessionId) as? Result.Failed)
                ?.failure
                ?.code,
        )
        assertRetained(token.sessionId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["read", "revoke", "recent"])
    fun assuranceIsRecheckedAfterWaitingForGuards(operation: String) {
        val author = actor(account(administrator = true))
        val target = account()
        val token = session(target)
        val access =
            object : IdentityRepository by identities {
                override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> =
                    identities.lockAccount(accountId, shared).also {
                        if (accountId == target)
                            clock.set(
                                clock.instant().plusSeconds(if (operation == "recent") 2 else 4)
                            )
                    }
            }
        val result =
            if (operation == "read") listing(access = access).execute(author, target)
            else revoking(access = access).execute(author, target, token.sessionId)
        assertEquals(
            if (operation == "recent") "recent_authentication_required" else "mfa_required",
            (result as? Result.Failed)?.failure?.code,
        )
        assertRetained(token.sessionId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["recent", "session", "failure", "interrupt"])
    fun lateFailureRollsBackRevocationAndItsJournal(mode: String) {
        val author = actor(account(administrator = true))
        val target = account()
        val token = session(target)
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    journal.record(actor, change).flatMap {
                        when (mode) {
                            "recent" -> {
                                clock.set(clock.instant().plusSeconds(2))
                                Result.Success(Unit)
                            }
                            "session" -> {
                                clock.set(clock.instant().plusSeconds(4))
                                Result.Success(Unit)
                            }
                            "failure" ->
                                Result.Failed(
                                    Failure(FailureKind.CONFLICT, "owned_journal_failure")
                                )
                            else -> throw InterruptedException("Owned session cancellation")
                        }
                    }
            }
        val execute = { revoking(changes = changes).execute(author, target, token.sessionId) }
        if (mode == "interrupt") assertThrows(InterruptedException::class.java) { execute() }
        else
            assertEquals(
                when (mode) {
                    "recent" -> "recent_authentication_required"
                    "session" -> "mfa_required"
                    else -> "owned_journal_failure"
                },
                (execute() as? Result.Failed)?.failure?.code,
            )
        assertRetained(token.sessionId)
        assertTrue(
            revoking()
                .execute(author.copy(mfaVerifiedAt = clock.instant()), target, token.sessionId)
                is Result.Success
        )
        assertEquals(1, audits(token.sessionId))
    }

    @Test
    fun idempotentRetryStillNeedsCurrentAuthorityAndDoesNotWriteAnotherAudit() {
        val author = actor(account(administrator = true))
        val target = account()
        val token = session(target)
        assertTrue(revoking().execute(author, target, token.sessionId) is Result.Success)
        clock.set(clock.instant().plusSeconds(2))
        assertEquals(
            "recent_authentication_required",
            (revoking().execute(author, target, token.sessionId) as? Result.Failed)?.failure?.code,
        )
        assertTrue(
            revoking()
                .execute(author.copy(mfaVerifiedAt = clock.instant()), target, token.sessionId)
                is Result.Success
        )
        assertEquals(1, audits(token.sessionId))
    }

    @Test
    fun revocationWaitsForRotationAndEndsTheSuccessorInTheSameFamily() {
        val author = actor(account(administrator = true))
        val target = account()
        val token = session(target)
        val rotated = CountDownLatch(1)
        val release = CountDownLatch(1)
        val revocationWaiting = CountDownLatch(1)
        val rotating =
            object : NativeSessionRepository by sessions {
                override fun rotate(
                    session: NativeSession,
                    oldToken: String?,
                    operationId: UUID,
                    replayUntil: Instant,
                ): Result<NativeTokens> =
                    sessions.rotate(session, oldToken, operationId, replayUntil).also {
                        rotated.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
            }
        val access =
            object : IdentityRepository by identities {
                override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> {
                    if (accountId == target) revocationWaiting.countDown()
                    return identities.lockAccount(accountId, shared)
                }
            }
        Executors.newFixedThreadPool(2).use { executor ->
            val refresh =
                executor.submit<Result<NativeTokens>> {
                    RefreshNativeSession(rotating, transactions, journal, clock, policy)
                        .execute(token.refreshToken, UUID.randomUUID(), UUID.randomUUID())
                }
            try {
                assertTrue(rotated.await(5, TimeUnit.SECONDS))
                val revoke =
                    executor.submit<Result<Unit>> {
                        revoking(access = access).execute(author, target, token.sessionId)
                    }
                assertTrue(revocationWaiting.await(5, TimeUnit.SECONDS))
                release.countDown()
                val successor = refresh.get(10, TimeUnit.SECONDS)
                assertTrue(successor is Result.Success, successor.toString())
                val ended = revoke.get(10, TimeUnit.SECONDS)
                assertTrue(ended is Result.Success, ended.toString())
                val credentials = (successor as Result.Success).value
                val stored = sessions.findAccess(credentials.accessToken)
                assertNotNull((stored as Result.Success).value?.revokedAt)
                assertEquals(1L, stored.value?.version)
                val next =
                    RefreshNativeSession(sessions, transactions, journal, clock, policy)
                        .execute(credentials.refreshToken, UUID.randomUUID(), UUID.randomUUID())
                assertEquals(FailureKind.UNAUTHENTICATED, (next as? Result.Failed)?.failure?.kind)
                assertEquals(1, audits(token.sessionId))
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun opposingAdministratorsAcquireAccountsInTheSameOrder() {
        val first = actor(account(administrator = true))
        val second = actor(account(administrator = true))
        val firstSession = session(first.accountId)
        val secondSession = session(second.accountId)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val actions =
                listOf(
                    executor.submit<Result<Unit>> {
                        check(start.await(5, TimeUnit.SECONDS))
                        revoking().execute(first, second.accountId, secondSession.sessionId)
                    },
                    executor.submit<Result<Unit>> {
                        check(start.await(5, TimeUnit.SECONDS))
                        revoking().execute(second, first.accountId, firstSession.sessionId)
                    },
                )
            start.countDown()
            actions.forEach { assertTrue(it.get(10, TimeUnit.SECONDS) is Result.Success) }
        }
        assertEquals(1, audits(firstSession.sessionId))
        assertEquals(1, audits(secondSession.sessionId))
    }

    @Test
    fun cancellationDuringPendingJournalingRollsBackAndReleasesTheConnection() {
        val author = actor(account(administrator = true))
        val target = account()
        val token = session(target)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    journal.record(actor, change).also {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
            }
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<Unit>> {
                    try {
                        revoking(changes = changes).execute(author, target, token.sessionId)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
            } finally {
                release.countDown()
            }
        }
        assertRetained(token.sessionId)
        assertTrue(revoking().execute(author, target, token.sessionId) is Result.Success)
        assertEquals(1, audits(token.sessionId))
    }
}

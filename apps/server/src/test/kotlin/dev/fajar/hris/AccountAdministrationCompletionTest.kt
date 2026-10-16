package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.identity.domain.usecases.*
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(TestClockConfiguration::class)
class AccountAdministrationCompletionTest : ApiIntegrationTest() {
    @Autowired private lateinit var accounts: AccountAdministrationRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var clock: MutableTestClock
    private val security =
        IdentitySecurityPolicy(
            maximumMfaAge = Duration.ofSeconds(4),
            recentAuthenticationAge = Duration.ofSeconds(2),
        )

    @BeforeEach
    fun resetClock() {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
    }

    private fun account(administrator: Boolean = false, enrolled: Boolean = true): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Owned account review',password_hash,? from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
                if (enrolled) "owned-fixture-only" else null,
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
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant(),
        )

    private fun saving(
        source: AccountAdministrationRepository = accounts,
        changes: ChangeJournalRepository = journal,
    ) = SaveAccountAccess(source, operations, changes, transactions, security, clock)

    @ParameterizedTest
    @ValueSource(strings = ["directory", "review"])
    fun completedReadsRequireCurrentMfaAndCanBeExplicitlyReacquired(mode: String) {
        val actor = actor(account(administrator = true))
        val target = account()
        val delayed =
            object : AccountAdministrationRepository by accounts {
                override fun list(
                    query: String,
                    after: UUID?,
                    limit: Int,
                ): Result<Page<ManagedAccount>> =
                    accounts.list(query, after, limit).also {
                        clock.set(clock.instant().plusSeconds(4))
                    }

                override fun get(id: UUID): Result<ManagedAccount?> =
                    accounts.get(id).also { clock.set(clock.instant().plusSeconds(4)) }
            }
        val result =
            if (mode == "directory")
                ListManagedAccounts(delayed, identities, transactions, security, clock)
                    .execute(actor, "", null, 25)
            else
                GetManagedAccount(delayed, identities, transactions, security, clock)
                    .execute(actor, target)
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val restored =
            if (mode == "directory")
                ListManagedAccounts(accounts, identities, transactions, security, clock)
                    .execute(renewed, "", null, 25)
            else
                GetManagedAccount(accounts, identities, transactions, security, clock)
                    .execute(renewed, target)
        assertTrue(restored is Result.Success, restored.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["recent", "session", "failure", "interrupt"])
    fun lateFailureRollsBackAccessCredentialsJournalAndReceipt(mode: String) {
        val actor = actor(account(administrator = true))
        val target = account()
        val key = UUID.randomUUID()
        val delayed =
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
                            else -> throw InterruptedException("Owned account review cancellation")
                        }
                    }
            }
        val execute = {
            saving(changes = delayed)
                .execute(
                    actor,
                    key,
                    target,
                    0,
                    false,
                    setOf("companies.create"),
                    "Reviewed platform access",
                )
        }
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
        assertUnchanged(target, key)
        val restored =
            saving()
                .execute(
                    actor.copy(mfaVerifiedAt = clock.instant()),
                    key,
                    target,
                    0,
                    false,
                    setOf("companies.create"),
                    "Reviewed platform access",
                )
        assertTrue(restored is Result.Success, restored.toString())
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    target,
                ),
        )
    }

    @Test
    fun currentEnrollmentIsRequiredEvenWhenTheAdmittedProofIsRecent() {
        val actor = actor(account(administrator = true, enrolled = false))
        val target = account()
        val key = UUID.randomUUID()
        val result =
            saving().execute(actor, key, target, 0, false, emptySet(), "Reviewed platform access")
        assertEquals("mfa_setup_required", (result as? Result.Failed)?.failure?.code)
        assertUnchanged(target, key)
        val read =
            GetManagedAccount(accounts, identities, transactions, security, clock)
                .execute(actor, target)
        assertEquals("mfa_setup_required", (read as? Result.Failed)?.failure?.code)
    }

    @Test
    fun permissionInputIsDetachedBeforeAdministrationWaitsAndOriginalReceiptStillMatches() {
        val actor = actor(account(administrator = true))
        val target = account()
        val key = UUID.randomUUID()
        val selected = mutableSetOf("companies.create")
        val delayed =
            object : AccountAdministrationRepository by accounts {
                override fun lockAdministration(): Result<Unit> =
                    accounts.lockAdministration().also {
                        selected.clear()
                        selected += "payroll.finalize"
                    }
            }
        val result =
            saving(source = delayed)
                .execute(actor, key, target, 0, true, selected, "Reviewed platform access")
        assertTrue(result is Result.Success, result.toString())
        assertEquals(
            listOf("companies.create"),
            database()
                .queryForList(
                    "select permission from platform_permissions where account_id=? order by permission",
                    String::class.java,
                    target,
                ),
        )
        val replay =
            saving()
                .execute(
                    actor,
                    key,
                    target,
                    0,
                    true,
                    setOf("companies.create"),
                    "Reviewed platform access",
                )
        assertTrue(replay is Result.Success, replay.toString())
        assertEquals(
            (result as Result.Success).value.copy(replayed = true),
            (replay as Result.Success).value,
        )
    }

    private fun assertUnchanged(target: UUID, key: UUID) {
        assertEquals(
            0L,
            database()
                .queryForObject("select version from accounts where id=?", Long::class.java, target),
        )
        assertEquals(
            0L,
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    target,
                ),
        )
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select active from accounts where id=?",
                    Boolean::class.java,
                    target,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from platform_permissions where account_id=?",
                    Int::class.java,
                    target,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=?",
                    Int::class.java,
                    target,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from outbox_events where resource_id=?",
                    Int::class.java,
                    target,
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
    }
}

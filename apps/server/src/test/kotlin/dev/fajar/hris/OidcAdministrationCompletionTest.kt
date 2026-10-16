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
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(TestClockConfiguration::class)
class OidcAdministrationCompletionTest : ApiIntegrationTest() {
    @Autowired private lateinit var links: OidcIdentityRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var clock: MutableTestClock
    private val issuer = "https://oidc.example.test"
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
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Owned SSO fixture',password_hash,? from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
                if (enrolled) "owned-oidc-fixture-only" else null,
            )
        if (administrator)
            database()
                .update(
                    "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
                    id,
                )
        return id
    }

    private fun administrator(enrolled: Boolean = true) =
        Actor(
            account(administrator = true, enrolled = enrolled),
            null,
            setOf("identity.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant(),
        )

    private fun saving(
        source: OidcIdentityRepository = links,
        changes: ChangeJournalRepository = journal,
        receipts: OperationRepository = operations,
        enabled: Boolean = true,
    ) =
        SaveOidcIdentity(
            source,
            identities,
            receipts,
            changes,
            transactions,
            security,
            OidcPolicy(enabled, issuer),
            clock,
        )

    private fun save(
        useCase: SaveOidcIdentity,
        actor: Actor,
        target: UUID,
        id: UUID,
        key: UUID = UUID.randomUUID(),
        version: Long? = null,
    ) =
        useCase.execute(
            actor,
            key,
            target,
            id,
            issuer,
            id.toString(),
            version == null,
            version,
            "Verified provider account",
        )

    @ParameterizedTest
    @ValueSource(strings = ["directory", "review"])
    fun completedReadsRequireCurrentMfaAndCanBeReacquired(mode: String) {
        val actor = administrator()
        val target = account()
        val id = UUID.randomUUID()
        assertTrue(save(saving(), actor, target, id) is Result.Success)
        val delayed =
            object : OidcIdentityRepository by links {
                override fun list(accountId: UUID, after: UUID?, size: Int) =
                    links.list(accountId, after, size).also {
                        clock.set(clock.instant().plusSeconds(4))
                    }

                override fun find(id: UUID) =
                    links.find(id).also { clock.set(clock.instant().plusSeconds(4)) }
            }
        val result =
            if (mode == "directory")
                ListOidcIdentities(delayed, identities, transactions, security, clock)
                    .execute(actor, target, null, 25)
            else
                GetOidcIdentity(delayed, identities, transactions, security, clock)
                    .execute(actor, target, id)
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val restored =
            if (mode == "directory")
                ListOidcIdentities(links, identities, transactions, security, clock)
                    .execute(renewed, target, null, 25)
            else
                GetOidcIdentity(links, identities, transactions, security, clock)
                    .execute(renewed, target, id)
        assertTrue(restored is Result.Success, restored.toString())
    }

    @ParameterizedTest
    @CsvSource(
        "create,journal,recent",
        "create,journal,session",
        "create,journal,interrupt",
        "create,receipt,recent",
        "create,receipt,session",
        "create,receipt,failure",
        "create,receipt,interrupt",
        "update,journal,recent",
        "update,journal,session",
        "update,journal,interrupt",
        "update,receipt,recent",
        "update,receipt,session",
        "update,receipt,failure",
        "update,receipt,interrupt",
    )
    fun lateFailureRollsBackBindingCredentialsAuditAndReceipt(
        kind: String,
        boundary: String,
        mode: String,
    ) {
        val actor = administrator()
        val target = account()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val version = if (kind == "update") 0L else null
        if (version != null) assertTrue(save(saving(), actor, target, id) is Result.Success)
        val before = snapshot(target, id)
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).flatMap {
                        if (boundary == "journal") completionOutcome(mode) else Result.Success(Unit)
                    }
            }
        val receipts =
            object : OperationRepository by operations {
                override fun record(actor: Actor, key: OperationKey, receipt: MutationReceipt) =
                    operations.record(actor, key, receipt).flatMap {
                        if (boundary == "receipt") completionOutcome(mode) else Result.Success(Unit)
                    }
            }
        val execute = {
            save(saving(changes = changes, receipts = receipts), actor, target, id, key, version)
        }
        if (mode == "interrupt") assertThrows(InterruptedException::class.java) { execute() }
        else
            assertEquals(
                when (mode) {
                    "recent" -> "recent_authentication_required"
                    "session" -> "mfa_required"
                    else -> "owned_oidc_failure"
                },
                (execute() as? Result.Failed)?.failure?.code,
            )
        assertEquals(before, snapshot(target, id))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    key,
                ),
        )
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val restored = save(saving(), renewed, target, id, key, version)
        assertTrue(restored is Result.Success, restored.toString())
        assertEquals((version ?: -1) + 1, (restored as Result.Success).value.version)
        val replay = save(saving(enabled = false), renewed, target, id, key, version)
        assertTrue(replay is Result.Success, replay.toString())
        assertTrue((replay as Result.Success).value.replayed)
        assertEquals(
            (version ?: -1) + 2,
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    target,
                ),
        )
    }

    private fun completionOutcome(mode: String): Result<Unit> =
        when (mode) {
            "recent",
            "session" -> {
                clock.set(clock.instant().plusSeconds(if (mode == "recent") 2 else 4))
                Result.Success(Unit)
            }
            "failure" -> Result.Failed(Failure(FailureKind.CONFLICT, "owned_oidc_failure"))
            else -> throw InterruptedException("Owned SSO cancellation")
        }

    @ParameterizedTest
    @ValueSource(strings = ["recent", "session"])
    fun receiptReplayRequiresCurrentProofAfterItsAccountWait(mode: String) {
        val actor = administrator()
        val target = account()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        assertTrue(save(saving(), actor, target, id, key) is Result.Success)
        val before = snapshot(target, id)
        val delayed =
            object : OidcIdentityRepository by links {
                override fun lockAccount(accountId: UUID) =
                    links.lockAccount(accountId).also {
                        clock.set(clock.instant().plusSeconds(if (mode == "recent") 2 else 4))
                    }
            }
        assertEquals(
            if (mode == "recent") "recent_authentication_required" else "mfa_required",
            (save(saving(source = delayed), actor, target, id, key) as? Result.Failed)
                ?.failure
                ?.code,
        )
        assertEquals(before, snapshot(target, id))
        val restored = save(saving(), actor.copy(mfaVerifiedAt = clock.instant()), target, id, key)
        assertTrue(restored is Result.Success, restored.toString())
        assertTrue((restored as Result.Success).value.replayed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["directory", "review", "save"])
    fun admittedProofDoesNotReplaceCurrentEnrollment(mode: String) {
        val actor = administrator(enrolled = false)
        val target = account()
        val id = UUID.randomUUID()
        val result =
            when (mode) {
                "directory" ->
                    ListOidcIdentities(links, identities, transactions, security, clock)
                        .execute(actor, target, null, 25)
                "review" ->
                    GetOidcIdentity(links, identities, transactions, security, clock)
                        .execute(actor, target, id)
                else -> save(saving(), actor, target, id)
            }
        assertEquals("mfa_setup_required", (result as? Result.Failed)?.failure?.code)
    }

    @Test
    fun selfBindingChangeCommitsAndThenRevokesItsOriginalCredential() {
        val actor = administrator()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val saved = save(saving(), actor, actor.accountId, id, key)
        assertTrue(saved is Result.Success, saved.toString())
        val revoked = save(saving(), actor, actor.accountId, id, key)
        assertEquals("session_revoked", (revoked as? Result.Failed)?.failure?.code)
        val replay = save(saving(), actor.copy(credentialVersion = 1), actor.accountId, id, key)
        assertTrue(replay is Result.Success, replay.toString())
        assertTrue((replay as Result.Success).value.replayed)
    }

    @Test
    fun bindingVersionCannotOverflow() {
        val actor = administrator()
        val target = account()
        val id = UUID.randomUUID()
        assertEquals(
            "invalid_oidc_identity",
            (save(saving(), actor, target, id, version = Long.MAX_VALUE) as? Result.Failed)
                ?.failure
                ?.code,
        )
        assertTrue(snapshot(target, id).drop(1).all { it.isEmpty() })
    }

    private fun snapshot(target: UUID, id: UUID) =
        listOf(
            database()
                .queryForList("select version,security_version from accounts where id=?", target),
            database().queryForList("select id,active,version from oidc_identities where id=?", id),
            database()
                .queryForList("select id from audit_entries where resource_id=? order by id", id),
            database()
                .queryForList("select id from outbox_events where resource_id=? order by id", id),
        )
}

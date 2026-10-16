package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.identity.domain.usecases.InviteAccount
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class InvitationCompletionAssuranceTest : CredentialApiFixture() {
    @Autowired private lateinit var accounts: AccountAdministrationRepository
    @Autowired private lateinit var credentials: CredentialChallengeRepository
    @Autowired private lateinit var mail: IdentityMailRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    private val security =
        IdentitySecurityPolicy(
            maximumMfaAge = Duration.ofSeconds(4),
            recentAuthenticationAge = Duration.ofSeconds(2),
        )

    private fun administrator(enrolled: Boolean = true): Actor {
        val id = localAccount()
        database()
            .update(
                "update accounts set mfa_secret_encrypted=? where id=?",
                if (enrolled) "owned-invitation-fixture" else null,
                id,
            )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
                id,
            )
        return Actor(
            id,
            null,
            setOf("identity.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant(),
        )
    }

    private fun inviting(
        source: AccountAdministrationRepository = accounts,
        changes: ChangeJournalRepository = journal,
        enabled: Boolean = true,
    ) =
        InviteAccount(
            source,
            credentials,
            mail,
            operations,
            changes,
            transactions,
            security,
            CredentialChallengePolicy(
                enabled = enabled,
                publicOrigin = "https://hris.example.test",
            ),
            clock,
        )

    private fun invite(
        useCase: InviteAccount,
        actor: Actor,
        target: UUID,
        key: UUID,
        version: Long? = null,
    ) =
        useCase.execute(
            actor,
            key,
            target,
            "$target@example.test",
            "Owned invitation",
            "Account provisioning",
            version,
        )

    @ParameterizedTest
    @CsvSource(
        "create,recent",
        "create,session",
        "create,failure",
        "create,interrupt",
        "resend,recent",
        "resend,session",
        "resend,failure",
        "resend,interrupt",
    )
    fun lateFailureRollsBackAccountChallengeDeliveryAuditAndReceipt(kind: String, mode: String) {
        val actor = administrator()
        val target = UUID.randomUUID()
        val key = UUID.randomUUID()
        val version = if (kind == "resend") 0L else null
        if (version != null)
            assertTrue(invite(inviting(), actor, target, UUID.randomUUID()) is Result.Success)
        val before = snapshot(target)
        val delayed =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    journal.record(actor, change).flatMap {
                        when (mode) {
                            "recent",
                            "session" -> {
                                clock.set(
                                    clock.instant().plusSeconds(if (mode == "recent") 2 else 4)
                                )
                                Result.Success(Unit)
                            }
                            "failure" ->
                                Result.Failed(
                                    Failure(FailureKind.CONFLICT, "owned_invitation_failure")
                                )
                            else -> throw InterruptedException("Owned invitation cancellation")
                        }
                    }
            }
        val execute = { invite(inviting(changes = delayed), actor, target, key, version) }
        if (mode == "interrupt") assertThrows(InterruptedException::class.java) { execute() }
        else
            assertEquals(
                when (mode) {
                    "recent" -> "recent_authentication_required"
                    "session" -> "mfa_required"
                    else -> "owned_invitation_failure"
                },
                (execute() as? Result.Failed)?.failure?.code,
            )
        assertEquals(before, snapshot(target))
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
        val restored = invite(inviting(), renewed, target, key, version)
        assertTrue(restored is Result.Success, restored.toString())
        assertEquals((version ?: -1) + 1, (restored as Result.Success).value.version)
        val replay = invite(inviting(enabled = false), renewed, target, key, version)
        assertTrue(replay is Result.Success, replay.toString())
        assertTrue((replay as Result.Success).value.replayed)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from identity_challenges where account_id=? and revoked_at is null and consumed_at is null",
                    Int::class.java,
                    target,
                ),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["recent", "session"])
    fun originalReceiptStillRequiresCurrentProofAfterAdministrationWait(mode: String) {
        val actor = administrator()
        val target = UUID.randomUUID()
        val key = UUID.randomUUID()
        assertTrue(invite(inviting(), actor, target, key) is Result.Success)
        val before = snapshot(target)
        val delayed =
            object : AccountAdministrationRepository by accounts {
                override fun lockAdministration(): Result<Unit> =
                    accounts.lockAdministration().also {
                        clock.set(clock.instant().plusSeconds(if (mode == "recent") 2 else 4))
                    }
            }
        assertEquals(
            if (mode == "recent") "recent_authentication_required" else "mfa_required",
            (invite(inviting(source = delayed), actor, target, key) as? Result.Failed)
                ?.failure
                ?.code,
        )
        assertEquals(before, snapshot(target))
        val restored = invite(inviting(), actor.copy(mfaVerifiedAt = clock.instant()), target, key)
        assertTrue(restored is Result.Success, restored.toString())
        assertTrue((restored as Result.Success).value.replayed)
    }

    @Test
    fun admittedProofDoesNotReplaceCurrentEnrollment() {
        val actor = administrator(enrolled = false)
        val target = UUID.randomUUID()
        val before = snapshot(target)
        assertEquals(
            "mfa_setup_required",
            (invite(inviting(), actor, target, UUID.randomUUID()) as? Result.Failed)?.failure?.code,
        )
        assertEquals(before, snapshot(target))
    }

    @Test
    fun invitationVersionCannotOverflow() {
        val actor = administrator()
        val target = UUID.randomUUID()
        assertEquals(
            "invalid_invitation",
            (invite(inviting(), actor, target, UUID.randomUUID(), Long.MAX_VALUE) as? Result.Failed)
                ?.failure
                ?.code,
        )
        assertTrue(snapshot(target).all { it.isEmpty() })
    }

    private fun snapshot(id: UUID) =
        listOf(
            database()
                .queryForList(
                    "select active,invitation_pending,version,security_version from accounts where id=?",
                    id,
                ),
            database()
                .queryForList(
                    "select id,consumed_at,revoked_at from identity_challenges where account_id=? order by id",
                    id,
                ),
            database()
                .queryForList(
                    "select challenge_id,state,md5(token_encrypted) as content from identity_mail_deliveries where account_id=? order by challenge_id",
                    id,
                ),
            database()
                .queryForList("select id from audit_entries where resource_id=? order by id", id),
        )
}

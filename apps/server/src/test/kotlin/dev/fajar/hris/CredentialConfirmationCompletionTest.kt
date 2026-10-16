package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.CredentialChallengeKind
import dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository
import dev.fajar.hris.identity.domain.repositories.IdentityMailRepository
import dev.fajar.hris.identity.domain.usecases.ConfirmAccountCredential
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

class CredentialConfirmationCompletionTest : CredentialApiFixture() {
    @Autowired private lateinit var credentials: CredentialChallengeRepository
    @Autowired private lateinit var mail: IdentityMailRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @CsvSource(
        "INVITATION,expiry",
        "INVITATION,interrupt",
        "PASSWORD_RECOVERY,expiry",
        "PASSWORD_RECOVERY,interrupt",
    )
    fun lateFailureRetainsOriginalCredentialsAndSingleUseLink(
        kind: CredentialChallengeKind,
        mode: String,
    ) {
        val originalTime = clock.instant()
        val recovery = kind == CredentialChallengeKind.PASSWORD_RECOVERY
        val id = if (recovery) localAccount() else UUID.randomUUID()
        val browser = client()
        if (recovery)
            assertEquals(202, recover(browser, csrf(browser), "$id@example.test").statusCode())
        else assertEquals(200, invite(browser, login(browser), id).statusCode())
        val (challenge, token) = link(id)
        val before =
            database()
                .queryForMap(
                    "select active,invitation_pending,version,security_version,md5(password_hash) as password from accounts where id=?",
                    id,
                )
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    journal.record(actor, change).also {
                        if (mode == "interrupt")
                            throw InterruptedException("Owned credential cancellation")
                        clock.set(originalTime.plus(Duration.ofDays(4)))
                    }
            }
        val command = ConfirmAccountCredential(credentials, mail, changes, transactions, clock)
        val execute = {
            command.execute(kind, token, "Owned-replacement-password-123!", UUID.randomUUID())
        }
        if (mode == "interrupt") assertThrows(InterruptedException::class.java) { execute() }
        else
            assertEquals(
                "credential_link_unavailable",
                (execute() as? Result.Failed)?.failure?.code,
            )
        assertEquals(
            before,
            database()
                .queryForMap(
                    "select active,invitation_pending,version,security_version,md5(password_hash) as password from accounts where id=?",
                    id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from identity_challenges where id=? and consumed_at is null and revoked_at is null",
                    Int::class.java,
                    challenge,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from identity_mail_deliveries where challenge_id=? and token_encrypted is not null",
                    Int::class.java,
                    challenge,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action in ('identity.invitation_accepted','identity.password_recovered')",
                    Int::class.java,
                    id,
                ),
        )
        clock.set(originalTime)
        val visitor = client()
        assertEquals(204, confirm(visitor, csrf(visitor), token, recovery).statusCode())
    }
}

package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.administration.domain.usecases.SaveCompanyClientPolicy
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class ClientPolicyCommandCompletionTest : ClientPolicyApiFixture() {
    @Autowired private lateinit var policies: CompanyClientPolicyRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    @ParameterizedTest
    @ValueSource(strings = ["password", "mfa"])
    fun expiryDuringJournalingRollsBackPolicyAndReceipt(mode: String) {
        val f = fixture()
        val original = clock.instant()
        val security = IdentitySecurityPolicy(enforceMfa = mode == "mfa")
        val proof = original.minus(security.recentAuthenticationAge).plusSeconds(1)
        val actor =
            f.actor.copy(
                authenticatedAt = proof,
                mfaVerifiedAt = if (mode == "mfa") proof else null,
            )
        if (mode == "mfa")
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    f.account,
                )
        var expire = true
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        assertTrue(it is Result.Success, it.toString())
                        if (expire) clock.set(original.plusSeconds(2))
                    }
            }
        val useCase =
            SaveCompanyClientPolicy(
                policies,
                companies,
                members,
                identities,
                operations,
                observedJournal,
                transactions,
                security,
                clock,
            )
        val operation = UUID.randomUUID()
        val input =
            SaveCompanyClientPolicyCommand(null, null, ClientPolicy(), "Completion assurance")
        try {
            val expired = useCase.execute(actor, operation, input)
            val code = "recent_authentication_required"
            assertEquals(code, (expired as? Result.Failed)?.failure?.code, expired.toString())
            assertEquals(0, versions(f))
            expire = false
            val renewed =
                actor.copy(
                    authenticatedAt = clock.instant(),
                    mfaVerifiedAt = if (mode == "mfa") clock.instant() else null,
                )
            val saved = useCase.execute(renewed, operation, input)
            assertTrue(saved is Result.Success, saved.toString())
            assertEquals(1, versions(f))
            assertEquals(
                Result.Success((saved as Result.Success).value.copy(replayed = true)),
                useCase.execute(renewed, operation, input),
            )
        } finally {
            database().update("update accounts set mfa_secret_encrypted=null where id=?", f.account)
            clock.set(original)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun currentEnrollmentIsRequiredForStrictAdmission(enforceMfa: Boolean) {
        val f = fixture()
        val security = IdentitySecurityPolicy(enforceMfa = enforceMfa)
        val useCase =
            SaveCompanyClientPolicy(
                policies,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val actor = f.actor.copy(mfaVerifiedAt = clock.instant())
        val input =
            SaveCompanyClientPolicyCommand(null, null, ClientPolicy(), "Enrollment assurance")
        val result = useCase.execute(actor, UUID.randomUUID(), input)
        if (enforceMfa) {
            assertEquals(
                "mfa_setup_required",
                (result as? Result.Failed)?.failure?.code,
                result.toString(),
            )
            assertEquals(0, versions(f))
        } else {
            assertTrue(result is Result.Success, result.toString())
            assertEquals(1, versions(f))
        }
    }
}

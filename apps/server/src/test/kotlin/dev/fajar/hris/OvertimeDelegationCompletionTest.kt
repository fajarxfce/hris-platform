package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.*
import dev.fajar.hris.workforce.domain.usecases.DecideOvertimeRequest
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

class OvertimeDelegationCompletionTest : OvertimeAssuranceApiFixture() {
    @Autowired private lateinit var overtime: OvertimeRepository
    @Autowired private lateinit var periods: WorkPeriodRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var approvals: ApprovalRepository

    @ParameterizedTest
    @CsvSource("APPROVE,candidates", "REJECT,candidates", "APPROVE,journal", "REJECT,journal")
    fun aNewDecisionRequiresCurrentStageDelegationThroughCompletion(
        decision: String,
        boundary: String,
    ) {
        val setup = prepare("list")
        val f = setup.fixture
        val delegate = reviewer(f)
        approvalPolicy(f, listOf(f.actor.accountId, delegate.id), version = 0)
        overtimeBody(actual(f, setup.id))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                delegate.id,
            )
        val admittedAt = clock.instant()
        val expires = admittedAt.plusSeconds(60)
        val delegation = UUID.randomUUID()
        fun grant(version: Long?, until: Instant) {
            overtimeBody(
                command(
                    f.admin,
                    "/api/v1/companies/${f.company}/approvals/delegations/$delegation",
                    json.writeValueAsString(
                        mapOf(
                            "kind" to "OVERTIME",
                            "fromAccount" to f.actor.accountId,
                            "toAccount" to delegate.id,
                            "validFrom" to admittedAt.minusSeconds(1).toString(),
                            "validUntil" to until.toString(),
                            "expectedVersion" to version,
                            "reason" to "Review coverage",
                        )
                    ),
                    f.csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            )
        }
        grant(null, expires)
        var expire = true
        var advanced = false
        fun advance() {
            if (expire) {
                clock.set(expires)
                advanced = true
            }
        }
        val observedMembers =
            object : MembershipRepository by members {
                override fun candidates(
                    companyId: UUID,
                    accountIds: Set<UUID>,
                    permissions: Set<String>,
                    limit: Int,
                ) =
                    members.candidates(companyId, accountIds, permissions, limit).also {
                        if (boundary == "candidates") advance()
                    }
            }
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also { if (boundary == "journal") advance() }
            }
        val actor =
            setup.actor.copy(
                accountId = delegate.id,
                permissions = setOf("company.read", "overtime.approve", "approvals.read"),
                mfaVerifiedAt = admittedAt,
            )
        val command =
            DecideOvertimeRequest(
                overtime,
                periods,
                people,
                companies,
                observedMembers,
                identities,
                operations,
                observedJournal,
                transactions,
                clock,
                approvals,
                IdentitySecurityPolicy(),
            )
        fun invoke() =
            command.execute(
                actor,
                setup.operation,
                setup.id,
                1,
                ApprovalDecision.valueOf(decision),
                "Work verified",
            )
        fun snapshot() =
            trackedTables.associateWith { table ->
                database()
                    .queryForList(
                        "select to_jsonb(t)::text from $table t where company_id=? order by 1",
                        String::class.java,
                        f.company,
                    )
            }
        val before = snapshot()
        val denied = invoke()
        expire = false
        assertTrue(advanced, "The selected acquisition/completion boundary was not reached")
        assertEquals(
            "not_assigned_approver",
            (denied as? Result.Failed)?.failure?.code,
            denied.toString(),
        )
        assertEquals(before, snapshot())
        grant(0, clock.instant().plusSeconds(120))
        val accepted = invoke()
        assertTrue(accepted is Result.Success, accepted.toString())
        val receipt = (accepted as Result.Success).value
        val after = snapshot()
        assertEquals(receipt.copy(replayed = true), (invoke() as Result.Success).value)
        assertEquals(after, snapshot())
        clock.set(clock.instant().plusSeconds(121))
        assertEquals(receipt.copy(replayed = true), (invoke() as Result.Success).value)
        assertEquals(after, snapshot())
    }
}

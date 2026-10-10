package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.approvals.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class ApprovalAssuranceTest : ApprovalApiFixture() {
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var probe: AccountLockProbe

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "inbox",
                "request",
                "templates",
                "delegations",
                "saveTemplate",
                "saveDelegation",
                "reassign",
            ]
    )
    fun proofExpiringDuringAccountAcquisitionCannotAuthorizeReadsWritesOrReceiptReplay(
        operation: String
    ) {
        val f = leaveFixture()
        val requestId = approvalId(f.company, pending(f))
        val permissions = setOf("approvals.read", "approvals.manage", "leave.approve")
        val reviewer = reviewer(f.company, permissions.toList())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                reviewer.account,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val actor =
            Actor(
                reviewer.account,
                f.company,
                permissions,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
            )
        val template =
            TemplateChange(
                id,
                "Reviewed policy",
                ApprovalKind.LEAVE,
                true,
                null,
                LocalDate.of(2026, 1, 1),
                "ASSURANCE",
                BigDecimal.ZERO,
                listOf(StageRule(AssignmentKind.NAMED, setOf(f.managerAccount), null)),
                "Approval configuration",
            )
        val delegation =
            Delegation(
                id,
                ApprovalKind.LEAVE,
                f.managerAccount,
                reviewer.account,
                clock.instant().minusSeconds(60),
                clock.instant().plusSeconds(3600),
                true,
                0,
            )
        val invoke: (Actor) -> Result<*> = { current ->
            when (operation) {
                "inbox" ->
                    ListApprovalInbox(approvals, identities, members, transactions, clock, security)
                        .execute(current, null, 20)
                "request" ->
                    GetApprovalRequest(
                            approvals,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, requestId)
                "templates" ->
                    ListApprovalTemplates(
                            approvals,
                            identities,
                            members,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, ApprovalKind.LEAVE, LocalDate.of(2026, 10, 1), null, 20)
                "delegations" ->
                    ListMyDelegations(approvals, identities, members, transactions, clock, security)
                        .execute(current, null, 20)
                "saveTemplate" ->
                    SaveApprovalTemplate(
                            approvals,
                            members,
                            companies,
                            identities,
                            operations,
                            journal,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, key, template)
                "saveDelegation" ->
                    SaveApprovalDelegation(
                            approvals,
                            members,
                            companies,
                            identities,
                            operations,
                            journal,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, key, delegation, null, "Approval coverage")
                else ->
                    ReassignApproval(
                            approvals,
                            members,
                            companies,
                            identities,
                            operations,
                            journal,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(
                            current,
                            key,
                            requestId,
                            0,
                            setOf(reviewer.account),
                            "Reviewer changed",
                        )
            }
        }
        val barrier = AccountLockProbe.Barrier(reviewer.account)
        probe.current.set(barrier)
        try {
            val expired =
                Executors.newSingleThreadExecutor().use { executor ->
                    val pending = executor.submit<Result<*>> { invoke(actor) }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        clock.set(clock.instant().plusSeconds(2))
                        barrier.release.countDown()
                        pending.get(10, TimeUnit.SECONDS)
                    } finally {
                        barrier.release.countDown()
                        probe.current.set(null)
                    }
                }
            assertEquals("mfa_required", (expired as? Result.Failed)?.failure?.code)
            assertEquals(
                0L,
                database()
                    .queryForObject(
                        "select version from approval_requests where company_id=? and id=?",
                        Long::class.java,
                        f.company,
                        requestId,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from audit_entries where company_id=? and actor_id=?",
                        Int::class.java,
                        f.company,
                        reviewer.account,
                    ),
            )
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            val result = invoke(renewed)
            assertTrue(result is Result.Success, result.toString())
            if (operation in setOf("saveTemplate", "saveDelegation", "reassign")) {
                val replayed =
                    Result.Success(
                        ((result as Result.Success).value as MutationReceipt).copy(replayed = true)
                    )
                assertEquals(replayed, invoke(renewed))
                clock.set(clock.instant().plus(security.maximumMfaAge))
                assertEquals("mfa_required", (invoke(renewed) as? Result.Failed)?.failure?.code)
                assertEquals(replayed, invoke(renewed.copy(mfaVerifiedAt = clock.instant())))
                assertEquals(
                    1,
                    database()
                        .queryForObject(
                            "select count(*) from audit_entries where company_id=? and actor_id=?",
                            Int::class.java,
                            f.company,
                            reviewer.account,
                        ),
                )
            }
        } finally {
            barrier.release.countDown()
            probe.current.set(null)
        }
    }
}

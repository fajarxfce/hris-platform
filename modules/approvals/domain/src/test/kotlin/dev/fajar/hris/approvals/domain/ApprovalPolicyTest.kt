package dev.fajar.hris.approvals.domain

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.MemberAccount
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApprovalPolicyTest {
    private val now = Instant.parse("2026-10-08T08:00:00Z")
    private val company = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val reviewer = UUID.randomUUID()

    private fun actor(id: UUID) = Actor(id, company, setOf("leave.approve"), now, UUID.randomUUID())

    private fun member(id: UUID) =
        MemberAccount(id, "$id@example.test", "Reviewer", true, true, setOf("leave.approve"), 0)

    private fun template(stages: List<StageRule>) =
        ApprovalTemplate(
            UUID.randomUUID(),
            "Leave",
            ApprovalKind.LEAVE,
            true,
            0,
            0,
            LocalDate.parse("2026-01-01"),
            null,
            BigDecimal.ZERO,
            stages,
        )

    private fun context(requester: UUID? = null) =
        ApprovalContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ApprovalKind.LEAVE,
            author,
            requester,
            reviewer,
            LocalDate.parse("2026-10-08"),
            null,
            BigDecimal.ZERO,
            now,
        )

    private fun request(stages: List<ApprovalStage>) =
        ApprovalRequest(
            UUID.randomUUID(),
            ApprovalKind.LEAVE,
            UUID.randomUUID(),
            author,
            null,
            UUID.randomUUID(),
            0,
            stages,
            0,
            ApprovalStatus.PENDING,
            0,
            now,
        )

    @Test
    fun snapshotsExcludeAuthorsBeneficiariesAndInactiveAccounts() {
        val beneficiary = UUID.randomUUID()
        val policy =
            template(listOf(StageRule(AssignmentKind.NAMED, setOf(author, beneficiary, reviewer))))
        val result =
            snapshotApproval(
                policy,
                context(beneficiary),
                listOf(
                    member(author),
                    member(beneficiary),
                    member(reviewer).copy(membershipActive = false),
                ),
            )
        val snapshot = (result as Result.Success).value
        assertEquals(ApprovalStatus.BLOCKED, snapshot.status)
        assertTrue(snapshot.stages.single().assignees.isEmpty())
        assertEquals(policy.revision, snapshot.templateRevision)
    }

    @Test
    fun aMissingLaterStageBlocksWithoutSkippingIt() {
        val request = request(listOf(ApprovalStage(setOf(reviewer)), ApprovalStage(emptySet())))
        val transition =
            decideApproval(
                request,
                actor(reviewer),
                ApprovalDecision.APPROVE,
                "",
                emptyList(),
                emptyList(),
                now,
            )
        val value = (transition as Result.Success).value
        assertEquals(ApprovalStatus.BLOCKED, value.status)
        assertEquals(1, value.currentStep)
    }

    @Test
    fun authorCannotApproveEvenWhenMistakenlyAssigned() {
        val result =
            decideApproval(
                request(listOf(ApprovalStage(setOf(author)))),
                actor(author),
                ApprovalDecision.APPROVE,
                "",
                emptyList(),
                emptyList(),
                now,
            )
        assertEquals("self_approval_denied", (result as Result.Failed).failure.code)
    }

    @Test
    fun delegationChecksExclusiveExpiryAndCurrentDelegatorAccess() {
        val substitute = UUID.randomUUID()
        val request = request(listOf(ApprovalStage(setOf(reviewer))))
        val delegation =
            Delegation(
                UUID.randomUUID(),
                ApprovalKind.LEAVE,
                reviewer,
                substitute,
                now.minusSeconds(60),
                now.plusSeconds(60),
                true,
                0,
            )
        val accepted =
            decideApproval(
                request,
                actor(substitute),
                ApprovalDecision.APPROVE,
                "",
                listOf(delegation),
                listOf(member(reviewer)),
                now,
            )
        assertEquals(reviewer, (accepted as Result.Success).value.decidingFor)
        val expired =
            decideApproval(
                request,
                actor(substitute),
                ApprovalDecision.APPROVE,
                "",
                listOf(delegation),
                listOf(member(reviewer)),
                delegation.validUntil,
            )
        assertTrue(expired is Result.Failed)
        val revoked =
            decideApproval(
                request,
                actor(substitute),
                ApprovalDecision.APPROVE,
                "",
                listOf(delegation),
                listOf(member(reviewer).copy(membershipActive = false)),
                now,
            )
        assertTrue(revoked is Result.Failed)
    }

    @Test
    fun equallySpecificPoliciesDoNotSilentlySelectOne() {
        val first = template(listOf(StageRule(AssignmentKind.MANAGER)))
        val second = first.copy(id = UUID.randomUUID())
        val result = selectApprovalTemplate(listOf(first, second), context())
        assertEquals("ambiguous_approval_policy", (result as Result.Failed).failure.code)
    }

    @Test
    fun monetaryPolicyRejectsExponentExpansionBeforeFingerprinting() {
        val change =
            TemplateChange(
                UUID.randomUUID(),
                "Policy",
                ApprovalKind.EXPENSE,
                true,
                null,
                LocalDate.parse("2026-01-01"),
                null,
                BigDecimal("1e1000000"),
                listOf(StageRule(AssignmentKind.MANAGER)),
                "Policy setup",
            )
        assertTrue(validateApprovalTemplate(change) is Result.Failed)
    }
}

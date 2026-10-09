package dev.fajar.hris.approvals.domain

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.MemberAccount
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApprovalMakerPolicyTest {
    private val now = Instant.parse("2026-10-08T08:00:00Z")
    private val company = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val maker = UUID.randomUUID()
    private val reviewer = UUID.randomUUID()

    private fun actor(id: UUID) =
        Actor(id, company, setOf("payroll.review"), now, UUID.randomUUID())

    private fun member(id: UUID) =
        MemberAccount(
            id,
            "$id@example.test",
            "Fictional reviewer",
            true,
            true,
            setOf("payroll.review"),
            0,
        )

    private fun request() =
        ApprovalRequest(
            UUID.randomUUID(),
            ApprovalKind.PAYROLL,
            UUID.randomUUID(),
            author,
            null,
            UUID.randomUUID(),
            0,
            listOf(ApprovalStage(setOf(maker, reviewer))),
            0,
            ApprovalStatus.PENDING,
            0,
            now,
            setOf(maker),
        )

    @Test
    fun snapshotRetainsAllMakersEvenWhenAnotherOfficerSubmitsTheAggregate() {
        val context =
            ApprovalContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ApprovalKind.PAYROLL,
                author,
                null,
                null,
                LocalDate.parse("2026-10-08"),
                null,
                BigDecimal.TEN,
                now,
                setOf(maker),
            )
        val template =
            ApprovalTemplate(
                UUID.randomUUID(),
                "Payroll",
                ApprovalKind.PAYROLL,
                true,
                0,
                0,
                LocalDate.parse("2026-01-01"),
                null,
                BigDecimal.ZERO,
                listOf(StageRule(AssignmentKind.NAMED, setOf(author, maker, reviewer))),
            )
        val result =
            snapshotApproval(
                template,
                context,
                listOf(member(author), member(maker), member(reviewer)),
            )
        val value = (result as Result.Success).value
        assertEquals(setOf(maker), value.excludedAccountIds)
        assertEquals(setOf(reviewer), value.stages.single().assignees)
        assertNull(value.requesterId)
        val tooMany = context.copy(excludedAccountIds = (1..201).map { UUID.randomUUID() }.toSet())
        assertEquals(
            "approval_exclusion_capacity",
            (snapshotApproval(template, tooMany, emptyList()) as Result.Failed).failure.code,
        )
    }

    @Test
    fun mistakenAssignmentsDoNotLetAMakerActDirectlyOrThroughDelegation() {
        val approval = request()
        val substitute = UUID.randomUUID()
        val delegation =
            Delegation(
                UUID.randomUUID(),
                ApprovalKind.PAYROLL,
                maker,
                substitute,
                now.minusSeconds(60),
                now.plusSeconds(60),
                true,
                0,
            )
        val direct =
            decideApproval(
                approval,
                actor(maker),
                ApprovalDecision.APPROVE,
                "",
                emptyList(),
                listOf(member(maker)),
                now,
            )
        assertEquals("self_approval_denied", (direct as Result.Failed).failure.code)
        val delegated =
            decideApproval(
                approval,
                actor(substitute),
                ApprovalDecision.APPROVE,
                "",
                listOf(delegation),
                listOf(member(maker)),
                now,
            )
        assertEquals("not_assigned_approver", (delegated as Result.Failed).failure.code)
        assertFalse(
            isAssignedApprover(actor(maker), approval, emptyList(), listOf(member(maker)), now)
        )
        assertFalse(
            isAssignedApprover(
                actor(substitute),
                approval,
                listOf(delegation),
                listOf(member(maker)),
                now,
            )
        )
        assertTrue(
            isAssignedApprover(
                actor(reviewer),
                approval,
                emptyList(),
                listOf(member(reviewer)),
                now,
            )
        )
    }

    @Test
    fun AMakerCannotReceiveDelegatedAuthorityFromAnIndependentAssignee() {
        val approval = request().copy(stages = listOf(ApprovalStage(setOf(reviewer))))
        val delegation =
            Delegation(
                UUID.randomUUID(),
                ApprovalKind.PAYROLL,
                reviewer,
                maker,
                now.minusSeconds(60),
                now.plusSeconds(60),
                true,
                0,
            )
        assertFalse(
            isAssignedApprover(
                actor(maker),
                approval,
                listOf(delegation),
                listOf(member(reviewer)),
                now,
            )
        )
        val denied =
            decideApproval(
                approval,
                actor(maker),
                ApprovalDecision.REJECT,
                "Reviewed",
                listOf(delegation),
                listOf(member(reviewer)),
                now,
            )
        assertEquals("self_approval_denied", (denied as Result.Failed).failure.code)
        val allowed =
            decideApproval(
                approval,
                actor(reviewer),
                ApprovalDecision.APPROVE,
                "",
                emptyList(),
                emptyList(),
                now,
            )
        assertEquals(ApprovalStatus.APPROVED, (allowed as Result.Success).value.status)
    }
}

package dev.fajar.hris.approvals.domain

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.identity.domain.entities.MemberAccount
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApprovalReadPolicyTest {
    private val now = Instant.parse("2026-10-01T15:00:00Z")
    private val owner = UUID.randomUUID()
    private val source = UUID.randomUUID()
    private val target = UUID.randomUUID()
    private val request =
        ApprovalRequest(
            UUID.randomUUID(),
            ApprovalKind.LEAVE,
            UUID.randomUUID(),
            owner,
            owner,
            UUID.randomUUID(),
            0,
            listOf(ApprovalStage(setOf(source))),
            0,
            ApprovalStatus.PENDING,
            0,
            now,
        )
    private val actor =
        Actor(target, UUID.randomUUID(), setOf("leave.team.approve"), now, UUID.randomUUID())
    private val member =
        MemberAccount(
            source,
            "example@example.test",
            "Example Manager",
            true,
            true,
            setOf("leave.team.approve"),
            0,
        )
    private val delegation =
        Delegation(
            UUID.randomUUID(),
            ApprovalKind.LEAVE,
            source,
            target,
            now,
            now.plusSeconds(60),
            true,
            0,
        )

    @Test
    fun delegationReadAccessExpiresAndRequiresCurrentSourcePermissions() {
        assertTrue(isAssignedApprover(actor, request, listOf(delegation), listOf(member), now))
        assertFalse(
            isAssignedApprover(
                actor,
                request,
                listOf(delegation),
                listOf(member.copy(permissions = emptySet())),
                now,
            )
        )
        assertFalse(
            isAssignedApprover(
                actor,
                request,
                listOf(delegation),
                listOf(member.copy(membershipActive = false)),
                now,
            )
        )
        assertFalse(
            isAssignedApprover(
                actor,
                request,
                listOf(delegation),
                listOf(member),
                now.plusSeconds(60),
            )
        )
    }

    @Test
    fun assignmentDoesNotRestoreARevokedApproverPermission() {
        assertTrue(
            isAssignedApprover(
                actor.copy(accountId = source),
                request,
                emptyList(),
                emptyList(),
                now,
            )
        )
        assertFalse(
            isAssignedApprover(
                actor.copy(accountId = source, permissions = setOf("approvals.read")),
                request,
                emptyList(),
                emptyList(),
                now,
            )
        )
    }
}

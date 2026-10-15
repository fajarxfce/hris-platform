package dev.fajar.hris.leave.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveCalendarScope
import dev.fajar.hris.leave.domain.policies.authorizeLeaveCalendar
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveCalendarAccessPolicyTest {
    @Test
    fun companyReaderMaySelectEitherScope() {
        val actor = actor(setOf("leave.read"))
        LeaveCalendarScope.entries.forEach {
            assertTrue(authorizeLeaveCalendar(actor, it) is Result.Success)
        }
    }

    @Test
    fun teamPermissionCannotWidenToCompany() {
        val actor = actor(setOf("leave.team.read"))
        assertTrue(authorizeLeaveCalendar(actor, LeaveCalendarScope.TEAM) is Result.Success)
        assertEquals(
            "access_denied",
            (authorizeLeaveCalendar(actor, LeaveCalendarScope.COMPANY) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun selfAndApprovalPermissionsDoNotGrantACalendar() {
        val actor =
            actor(
                setOf(
                    "leave.self.manage",
                    "leave.team.approve",
                    "leave.approve",
                    "approvals.read",
                    "people.read",
                )
            )
        LeaveCalendarScope.entries.forEach {
            assertTrue(authorizeLeaveCalendar(actor, it) is Result.Failed)
        }
    }

    private fun actor(permissions: Set<String>) =
        Actor(UUID.randomUUID(), UUID.randomUUID(), permissions, Instant.EPOCH, UUID.randomUUID())
}

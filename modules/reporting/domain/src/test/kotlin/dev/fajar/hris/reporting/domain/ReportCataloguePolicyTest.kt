package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.ReportKind
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportCataloguePolicyTest {
    private val actor =
        Actor(UUID.randomUUID(), UUID.randomUUID(), emptySet(), Instant.now(), UUID.randomUUID())

    @Test
    fun companySourceGrantsSelectOnlyTheirReportsAndNeverImplicitlySupplyReportingAccess() {
        val sources =
            mapOf(
                "people.read" to listOf(ReportKind.HEADCOUNT),
                "workforce.read" to
                    listOf(
                        ReportKind.ATTENDANCE_EXCEPTIONS,
                        ReportKind.ATTENDANCE_CLOSINGS,
                        ReportKind.OVERTIME,
                    ),
                "leave.read" to listOf(ReportKind.LEAVE),
                "overtime.read" to listOf(ReportKind.OVERTIME),
                "overtime.manage" to listOf(ReportKind.OVERTIME),
                "expenses.read" to listOf(ReportKind.EXPENSES),
                "expenses.manage" to listOf(ReportKind.EXPENSES),
                "payroll.read" to listOf(ReportKind.PAYROLL_COSTS),
            )
        for ((permission, kinds) in sources) {
            assertEquals(
                kinds,
                availableReportKinds(actor.copy(permissions = setOf("reports.read", permission))),
                permission,
            )
            assertEquals(
                emptyList<ReportKind>(),
                availableReportKinds(actor.copy(permissions = setOf(permission))),
                permission,
            )
        }
        assertEquals(
            ReportKind.entries,
            availableReportKinds(actor.copy(permissions = sources.keys + "reports.read")),
        )
        assertEquals(7, ReportKind.entries.map { it.definitionVersion }.toSet().size)
    }

    @Test
    fun selfTeamAndDecisionGrantsNeverBecomeCompanyReports() {
        val restricted =
            setOf(
                "reports.read",
                "people.self.read",
                "people.team.read",
                "workforce.team.read",
                "leave.self.manage",
                "leave.team.read",
                "overtime.self.manage",
                "overtime.team.read",
                "overtime.approve",
                "expenses.self.manage",
                "expenses.team.read",
                "expenses.approve",
                "payroll.self.read",
                "payroll.manage",
                "approvals.read",
            )
        assertTrue(availableReportKinds(actor.copy(permissions = restricted)).isEmpty())
    }
}

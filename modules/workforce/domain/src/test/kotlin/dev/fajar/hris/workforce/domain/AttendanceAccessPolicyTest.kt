package dev.fajar.hris.workforce.domain

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.canReviewAttendance
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttendanceAccessPolicyTest {
    @Test
    fun rebindingEmploymentCannotBypassOriginalOrCurrentBeneficiaryExclusion() {
        val owner = UUID.randomUUID()
        val replacement = UUID.randomUUID()
        val reviewer = UUID.randomUUID()
        val company = UUID.randomUUID()
        val date = LocalDate.parse("2026-01-01")
        val employee =
            Employee(
                UUID.randomUUID(),
                company,
                "E001",
                PersonProfile(UUID.randomUUID(), replacement, "Example employee", null, "ID", null),
                EmploymentTerms(
                    date,
                    ContractKind.PERMANENT,
                    date,
                    null,
                    EmploymentStatus.ACTIVE,
                    null,
                    null,
                    null,
                    null,
                    null,
                ),
                null,
                1,
                1,
            )
        val actor =
            Actor(
                reviewer,
                company,
                setOf("attendance.verify"),
                Instant.parse("2026-10-01T00:00:00Z"),
                UUID.randomUUID(),
            )
        assertTrue(canReviewAttendance(actor, employee, owner))
        assertFalse(canReviewAttendance(actor.copy(accountId = owner), employee, owner))
        assertFalse(canReviewAttendance(actor.copy(accountId = replacement), employee, owner))
    }
}

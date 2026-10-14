package dev.fajar.hris.people.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DirectoryProjectionPolicyTest {
    private val today = LocalDate.parse("2026-10-01")
    private val zone = ZoneId.of("Asia/Jakarta")
    private val entry = DirectoryEntry("M001", "Example coworker", null, null, null, null)
    private val candidate =
        DirectoryCandidate(UUID.randomUUID(), entry, EmploymentStatus.ACTIVE, today, null, null)

    private fun plan(
        input: DirectoryCandidate = candidate,
        current: DirectoryRecord? = null,
        date: LocalDate = today,
        zone: ZoneId = this.zone,
    ): DirectoryProjection =
        (projectDirectoryEmployee(input, current, date, zone) as Result.Success).value

    @Test
    fun aFutureEmployeeIsNotPublishedUntilTheirCompanyLocalStart() {
        val input = candidate.copy(start = today.plusDays(1))
        assertNull(plan(input).changed)
        assertEquals(Instant.parse("2026-10-01T17:00:00Z"), plan(input).nextRefresh)
        assertEquals(entry, plan(input, date = today.plusDays(1)).changed?.employee)
    }

    @Test
    fun anInclusiveEndDateRemovesThePreviouslyPublishedEmployeeOnTheFollowingDate() {
        val current = DirectoryRecord(candidate.id, 12, entry)
        val input = candidate.copy(end = today)
        assertNull(plan(input, current).changed)
        assertEquals(Instant.parse("2026-10-01T17:00:00Z"), plan(input, current).nextRefresh)
        assertEquals(
            DirectoryRecord(candidate.id, 13, null),
            plan(input, current, today.plusDays(1)).changed,
        )
    }

    @Test
    fun suspendedAndEndedEmployeesDoNotDiscloseEvenTheirExistenceBeforeFirstPublication() {
        for (status in listOf(EmploymentStatus.SUSPENDED, EmploymentStatus.ENDED, null)) {
            assertNull(plan(candidate.copy(status = status)).changed)
        }
        assertEquals(
            entry,
            plan(candidate.copy(status = EmploymentStatus.PROBATION)).changed?.employee,
        )
    }

    @Test
    fun identicalPublicContentDoesNotCreateNewVersionsButTheNextRevisionIsStillScheduled() {
        val current = DirectoryRecord(candidate.id, 9, entry)
        val input = candidate.copy(nextRevision = today.plusDays(2))
        assertNull(plan(input, current).changed)
        assertEquals(
            today.plusDays(2).atStartOfDay(zone).toInstant(),
            plan(input, current).nextRefresh,
        )
    }

    @Test
    fun aReturnedCoworkerReusesIdentityAndAdvancesBeyondTheTombstone() {
        assertEquals(
            DirectoryRecord(candidate.id, 7, entry),
            plan(current = DirectoryRecord(candidate.id, 6, null)).changed,
        )
    }

    @Test
    fun changedAssignmentLabelsAdvanceOnlyThisRecord() {
        val updated = entry.copy(departmentId = UUID.randomUUID(), department = "Engineering")
        assertEquals(
            DirectoryRecord(candidate.id, 4, updated),
            plan(candidate.copy(entry = updated), DirectoryRecord(candidate.id, 3, entry)).changed,
        )
    }

    @Test
    fun dateTransitionsUseCompanyTimezoneAcrossDaylightSaving() {
        val date = LocalDate.parse("2026-03-08")
        val input = candidate.copy(start = date, end = date)
        val next = plan(input, date = date, zone = ZoneId.of("America/New_York")).nextRefresh
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), next)
    }

    @Test
    fun exhaustedVersionsFailInsteadOfWrappingToAnOlderRevision() {
        val result =
            projectDirectoryEmployee(
                candidate.copy(entry = entry.copy(name = "Updated")),
                DirectoryRecord(candidate.id, Long.MAX_VALUE, entry),
                today,
                zone,
            )
        assertEquals("directory_revision_exhausted", (result as Result.Failed).failure.code)
    }
}

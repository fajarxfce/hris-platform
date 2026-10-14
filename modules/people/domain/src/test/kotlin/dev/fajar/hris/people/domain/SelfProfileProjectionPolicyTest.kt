package dev.fajar.hris.people.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelfProfileProjectionPolicyTest {
    private val today = LocalDate.parse("2026-10-01")
    private val zone = ZoneId.of("Asia/Jakarta")
    private val company = UUID.randomUUID()
    private val employee =
        Employee(
            UUID.randomUUID(),
            company,
            "E001",
            PersonProfile(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Employee",
                LocalDate.parse("1990-02-03"),
                "ID",
                "employee@example.test",
            ),
            EmploymentTerms(
                today,
                ContractKind.PERMANENT,
                today,
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
            ),
            UUID.randomUUID(),
            4,
            2,
        )
    private val candidate =
        SelfProfileCandidate(
            employee.id,
            employee,
            company,
            7,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )

    private fun plan(
        input: SelfProfileCandidate = candidate,
        current: SelfProfileRecord? = null,
        date: LocalDate = today,
        zone: ZoneId = this.zone,
    ) = (projectSelfProfile(input, current, date, zone) as Result.Success).value

    @Test
    fun aProfileContainsCoherentOwnedFieldsWithoutTheManagersPrivateAccount() {
        val snapshot = requireNotNull(plan().changed?.snapshot)
        assertEquals(employee.person, snapshot.details.employee.person)
        assertNull(snapshot.details.employee.managerAccountId)
        assertEquals(7, snapshot.profileVersion)
        assertEquals(company, snapshot.ownerCompanyId)
        assertFalse(snapshot.toString().contains("employee@example.test"))
    }

    @Test
    fun ordinaryDateChangesKeepTheVersionAndRecordedProvenance() {
        val initial = requireNotNull(plan().changed)
        assertNull(plan(current = initial, date = today.plusDays(73)).changed)
        assertEquals(today, initial.snapshot?.details?.asOf)
    }

    @Test
    fun privateProfileChangesAdvanceIndependentlyOfEmploymentRevisions() {
        val initial = requireNotNull(plan().changed)
        val changed =
            candidate.copy(
                employee =
                    employee.copy(
                        person = employee.person.copy(birthDate = LocalDate.parse("1991-02-03"))
                    ),
                profileVersion = 8,
            )
        val record = requireNotNull(plan(changed, initial).changed)
        assertEquals(1, record.version)
        assertEquals(8, record.snapshot?.profileVersion)
        assertEquals(4, record.snapshot?.details?.employee?.version)
        assertEquals(2, record.snapshot?.details?.employee?.appliedRevision)
    }

    @Test
    fun labelsAndTheirAvailabilityInvalidateTheOwnedProjection() {
        val id = UUID.randomUUID()
        val input =
            candidate.copy(
                employee = employee.copy(terms = employee.terms.copy(departmentId = id)),
                department = EmploymentUnitReference(id, "ENG", "Engineering", true),
            )
        val initial = requireNotNull(plan(input).changed)
        val updated =
            input.copy(department = input.department?.copy(name = "Product", active = false))
        val record = requireNotNull(plan(updated, initial).changed)
        assertEquals(1, record.version)
        assertEquals("Product", record.snapshot?.details?.department?.name)
        assertEquals(false, record.snapshot?.details?.department?.active)
    }

    @Test
    fun managerAvailabilityChangesAfterItsInclusiveEndInCompanyTime() {
        val manager =
            SelfProfileManagerCandidate(
                UUID.randomUUID(),
                "M001",
                "Manager",
                EmploymentStatus.ACTIVE,
                today.minusYears(1),
                today,
            )
        val input = candidate.copy(manager = manager)
        val first = plan(input)
        assertEquals(Instant.parse("2026-10-01T17:00:00Z"), first.nextRefresh)
        assertEquals(true, first.changed?.snapshot?.details?.manager?.working)
        val next = plan(input, first.changed, today.plusDays(1))
        assertEquals(false, next.changed?.snapshot?.details?.manager?.working)
        assertEquals(1, next.changed?.version)
    }

    @Test
    fun upcomingRevisionsScheduleTheirCompanyDateWithoutPublishingAnAbsentEmployment() {
        val input = candidate.copy(employee = null, nextRevision = today.plusDays(1))
        val projected = plan(input)
        assertNull(projected.changed)
        assertEquals(Instant.parse("2026-10-01T17:00:00Z"), projected.nextRefresh)
        val dstDate = LocalDate.parse("2026-03-08")
        assertEquals(
            Instant.parse("2026-03-09T04:00:00Z"),
            plan(
                    input.copy(nextRevision = dstDate.plusDays(1)),
                    date = dstDate,
                    zone = ZoneId.of("America/New_York"),
                )
                .nextRefresh,
        )
    }

    @Test
    fun unlinkingWithdrawsTheRecordAndReappearanceAdvancesBeyondItsTombstone() {
        val initial = requireNotNull(plan().changed)
        val unlinked =
            candidate.copy(
                employee = employee.copy(person = employee.person.copy(accountId = null))
            )
        val deleted = requireNotNull(plan(unlinked, initial).changed)
        assertEquals(SelfProfileRecord(employee.id, 1, null), deleted)
        assertEquals(2, plan(current = deleted).changed?.version)
        assertNull(plan(unlinked).changed)
    }

    @Test
    fun endedEmploymentStillHasItsOwnProfileAndMaximumManagerDateDoesNotOverflow() {
        val manager =
            SelfProfileManagerCandidate(
                UUID.randomUUID(),
                "M001",
                "Manager",
                EmploymentStatus.ENDED,
                today,
                LocalDate.MAX,
            )
        val input =
            candidate.copy(
                employee =
                    employee.copy(terms = employee.terms.copy(status = EmploymentStatus.ENDED)),
                manager = manager,
            )
        assertNotNull(plan(input).changed?.snapshot)
        assertNull(plan(input).nextRefresh)
    }

    @Test
    fun revisionExhaustionAndPartialReadGrantsCannotWidenTheProjection() {
        val initial = requireNotNull(plan().changed)
        val updated = candidate.copy(profileVersion = 8)
        val failure =
            projectSelfProfile(updated, initial.copy(version = Long.MAX_VALUE), today, zone)
                as Result.Failed
        assertEquals("profile_revision_exhausted", failure.failure.code)
        assertTrue(canReadSelfProfile(setOf("people.self.read")))
        assertTrue(canReadSelfProfile(setOf("people.read", "people.profile.read")))
        assertFalse(canReadSelfProfile(setOf("people.read")))
        assertFalse(canReadSelfProfile(setOf("people.profile.read")))
        assertFalse(canReadSelfProfile(setOf("people.directory.read")))
    }
}

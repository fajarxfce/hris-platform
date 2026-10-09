package dev.fajar.hris.communications.domain

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementPublicationPolicyTest {
    private val now = Instant.parse("2026-10-01T00:00:00Z")

    @Test
    fun schedulingHasAnExplicitPrecisionAndFiniteWindow() {
        assertEquals(Result.Success(Unit), validateAnnouncementSchedule(null, now))
        assertEquals(Result.Success(Unit), validateAnnouncementSchedule(now, now))
        assertEquals(
            Result.Success(Unit),
            validateAnnouncementSchedule(now.plusSeconds(365L * 86400), now),
        )
        for (invalid in
            listOf(now.minusSeconds(1), now.plusNanos(1), now.plusSeconds(365L * 86400 + 1))) {
            val failure = validateAnnouncementSchedule(invalid, now) as Result.Failed
            assertEquals("invalid_announcement_schedule", failure.failure.code)
            assertEquals(mapOf("maximumDays" to "365"), failure.failure.parameters)
        }
    }

    @Test
    fun onlyActiveEligibleReadersAreSelectedOnTheCompanyDate() {
        val audience = AnnouncementAudience(AudienceKind.COMPANY)
        val date = LocalDate.of(2026, 10, 1)
        val policy = announcementRecipientSelection(audience, date)
        assertEquals(date, policy.date)
        assertEquals(audience, policy.audience)
        assertEquals(
            setOf(EmploymentStatus.ACTIVE, EmploymentStatus.PROBATION),
            policy.employmentStatuses,
        )
        assertTrue(policy.accountActive)
        assertTrue(policy.membershipActive)
        assertEquals("announcements.read", policy.requiredPermission)
    }

    @Test
    fun commandsRequireObservedVersionsAndBoundedReasons() {
        assertTrue(validateAnnouncementPublicationInput(-1, "reason") is Result.Failed)
        assertTrue(validateAnnouncementPublicationInput(0, " ") is Result.Failed)
        assertTrue(validateAnnouncementPublicationInput(0, "r".repeat(1001)) is Result.Failed)
        assertEquals(
            Result.Success(Unit),
            validateAnnouncementPublicationInput(0, "Publish update"),
        )
    }
}

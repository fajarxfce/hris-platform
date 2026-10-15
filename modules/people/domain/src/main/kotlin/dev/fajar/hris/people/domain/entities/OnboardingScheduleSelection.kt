package dev.fajar.hris.people.domain.entities

import java.time.LocalDate
import java.util.UUID

/** The first working day and company-local date at which HR wants the checklist prepared. */
data class OnboardingScheduleSelection(
    val templateId: UUID,
    val targetDate: LocalDate,
    val startsOn: LocalDate,
)

package dev.fajar.hris.communications.domain.entities

import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.time.LocalDate

/** Predicates selected by the publication use case, applied by its data projection. */
data class AnnouncementRecipientSelection(
    val audience: AnnouncementAudience,
    val date: LocalDate,
    val employmentStatuses: Set<EmploymentStatus>,
    val accountActive: Boolean,
    val membershipActive: Boolean,
    val requiredPermission: String,
)

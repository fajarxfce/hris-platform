package dev.fajar.hris.communications.data.models

import java.time.LocalDate
import java.util.UUID

data class AnnouncementRecipientQuery(
    val companyId: UUID,
    val date: LocalDate,
    val audienceKind: String,
    val targetIds: List<UUID>,
    val employmentStatuses: Set<String>,
    val accountActive: Boolean,
    val membershipActive: Boolean,
    val requiredPermission: String,
)

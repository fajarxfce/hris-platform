package dev.fajar.hris.workforce.domain.entities

import java.util.UUID

data class AttendanceAssessment(
    val status: AttendanceStatus,
    val issues: Set<AttendanceIssue>,
    val closingJobId: UUID? = null,
)

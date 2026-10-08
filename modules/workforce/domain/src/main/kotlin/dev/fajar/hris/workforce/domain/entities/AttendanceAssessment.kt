package dev.fajar.hris.workforce.domain.entities

data class AttendanceAssessment(val status: AttendanceStatus, val issues: Set<AttendanceIssue>)

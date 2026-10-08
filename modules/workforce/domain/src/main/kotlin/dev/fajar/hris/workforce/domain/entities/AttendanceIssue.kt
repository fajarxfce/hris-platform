package dev.fajar.hris.workforce.domain.entities
enum class AttendanceIssue {
    OFFLINE,
    UNVERIFIED_CAPTURE,
    WINDOW_EXPIRED,
    LOCATION_REQUIRED,
    LOW_ACCURACY,
    OUTSIDE_FENCE,
    MOCK_LOCATION,
    UNSCHEDULED,
    OUTSIDE_SHIFT_WINDOW,
    SEQUENCE_REVIEW,
}

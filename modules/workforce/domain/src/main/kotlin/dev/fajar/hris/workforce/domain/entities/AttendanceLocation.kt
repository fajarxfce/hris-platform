package dev.fajar.hris.workforce.domain.entities

data class AttendanceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val mocked: Boolean,
)

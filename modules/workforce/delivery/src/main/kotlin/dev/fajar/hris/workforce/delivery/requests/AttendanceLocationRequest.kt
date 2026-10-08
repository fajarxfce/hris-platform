package dev.fajar.hris.workforce.delivery.requests

data class AttendanceLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val mocked: Boolean = false,
)

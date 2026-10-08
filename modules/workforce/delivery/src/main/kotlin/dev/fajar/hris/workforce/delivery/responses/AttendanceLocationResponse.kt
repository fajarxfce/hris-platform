package dev.fajar.hris.workforce.delivery.responses

data class AttendanceLocationResponse(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val mocked: Boolean,
)

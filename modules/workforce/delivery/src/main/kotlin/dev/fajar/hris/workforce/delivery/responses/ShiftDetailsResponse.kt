package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalTime

data class ShiftDetailsResponse(
    val code: String,
    val name: String,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val breakMinutes: Int,
    val timezone: String,
    val mode: String,
    val locationRequired: Boolean,
    val maxAccuracyMeters: Double,
    val fence: GeoFenceResponse?,
)

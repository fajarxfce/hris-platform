package dev.fajar.hris.workforce.domain.entities

import java.time.LocalTime

data class ShiftDetails(
    val code: String,
    val name: String,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val breakMinutes: Int,
    val timezone: String,
    val mode: WorkMode,
    val locationRequired: Boolean,
    val maxAccuracyMeters: Double,
    val fence: GeoFence?,
)

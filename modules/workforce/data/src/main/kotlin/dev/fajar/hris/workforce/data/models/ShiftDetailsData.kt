package dev.fajar.hris.workforce.data.models
data class ShiftDetailsData(
    val code: String,
    val name: String,
    val startsAt: String,
    val endsAt: String,
    val breakMinutes: Int,
    val timezone: String,
    val mode: String,
    val locationRequired: Boolean,
    val maxAccuracyMeters: Double,
    val fence: GeoFenceData?,
)

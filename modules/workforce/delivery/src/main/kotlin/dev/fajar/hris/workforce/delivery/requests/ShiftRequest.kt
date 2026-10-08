package dev.fajar.hris.workforce.delivery.requests

import dev.fajar.hris.workforce.domain.entities.WorkMode
import java.time.LocalTime

data class ShiftRequest(
    val code: String,
    val name: String,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val breakMinutes: Int,
    val timezone: String,
    val mode: WorkMode,
    val locationRequired: Boolean = false,
    val maxAccuracyMeters: Double = 50.0,
    val fence: GeoFenceRequest? = null,
    val active: Boolean = true,
    val expectedVersion: Long? = null,
    val reason: String,
)

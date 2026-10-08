package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.requests.*
import dev.fajar.hris.workforce.domain.entities.*

fun ShiftRequest.toDetails(): ShiftDetails =
    ShiftDetails(
        code,
        name,
        startsAt,
        endsAt,
        breakMinutes,
        timezone,
        mode,
        locationRequired,
        maxAccuracyMeters,
        fence?.let { GeoFence(it.latitude, it.longitude, it.radiusMeters) },
    )

fun ShiftReferenceRequest.toReference(): ShiftReference = ShiftReference(id, version)

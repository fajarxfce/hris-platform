package dev.fajar.hris.administration.delivery.responses

import java.time.Instant

data class MaintenanceWindowResponse(val startsAt: Instant, val endsAt: Instant)

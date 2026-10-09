package dev.fajar.hris.administration.delivery.requests

import java.time.Instant

data class MaintenanceWindowRequest(val startsAt: Instant, val endsAt: Instant)

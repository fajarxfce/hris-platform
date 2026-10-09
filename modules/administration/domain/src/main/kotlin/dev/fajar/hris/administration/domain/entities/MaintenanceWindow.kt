package dev.fajar.hris.administration.domain.entities

import java.time.Instant

data class MaintenanceWindow(val startsAt: Instant, val endsAt: Instant)

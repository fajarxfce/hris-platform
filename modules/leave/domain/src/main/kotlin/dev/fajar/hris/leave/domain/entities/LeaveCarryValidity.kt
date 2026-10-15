package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.time.LocalDate

/** Frozen company-local validity; the boundary instant is exclusive. */
data class LeaveCarryValidity(val through: LocalDate, val expiresAt: Instant)

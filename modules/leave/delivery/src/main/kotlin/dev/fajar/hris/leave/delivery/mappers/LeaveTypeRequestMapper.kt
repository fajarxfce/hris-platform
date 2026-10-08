package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.leave.delivery.requests.LeaveTypeRequest
import dev.fajar.hris.leave.domain.entities.*
import java.util.Locale
import java.util.UUID

fun LeaveTypeRequest.toType(id: UUID): LeaveType =
    LeaveType(
        id,
        code.trim().uppercase(Locale.ROOT),
        effectiveFrom,
        LeavePolicy(
            name.trim(),
            paid,
            allowPartialDays,
            minServiceMonths,
            allowedContracts.toSet(),
            maxRequestDays,
        ),
        active,
        expectedVersion ?: 0,
        expectedVersion ?: 0,
    )

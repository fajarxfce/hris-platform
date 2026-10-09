package dev.fajar.hris.leave.delivery.requests

import dev.fajar.hris.people.domain.entities.ContractKind
import java.time.LocalDate

data class LeaveTypeRequest(
    val code: String,
    val name: String,
    val effectiveFrom: LocalDate,
    val paid: Boolean,
    val allowPartialDays: Boolean = true,
    val minServiceMonths: Int = 0,
    val allowedContracts: Set<ContractKind> = ContractKind.entries.toSet(),
    val maxRequestDays: Int = 30,
    val active: Boolean = true,
    val expectedVersion: Long? = null,
    val reason: String,
    val attachmentRequired: Boolean = false,
)

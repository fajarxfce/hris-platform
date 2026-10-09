package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.leave.data.models.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.people.domain.entities.ContractKind
import tools.jackson.databind.ObjectMapper

fun LeavePolicy.toData(): LeavePolicyData =
    LeavePolicyData(
        name,
        paid,
        allowPartialDays,
        minServiceMonths,
        allowedContracts.map { it.name }.toSet(),
        maxRequestDays,
        attachmentRequired,
        accrual?.let {
            LeaveAccrualPolicyData(it.frequency.name, it.halfDaysPerPeriod, it.carryLimitHalfDays)
        },
    )

fun LeavePolicyData.toPolicy(): LeavePolicy =
    LeavePolicy(
        name,
        paid,
        allowPartialDays,
        minServiceMonths,
        allowedContracts.map { ContractKind.valueOf(it) }.toSet(),
        maxRequestDays,
        attachmentRequired,
        accrual?.let {
            LeaveAccrualPolicy(
                LeaveAccrualFrequency.valueOf(it.frequency),
                it.halfDaysPerPeriod,
                it.carryLimitHalfDays,
            )
        },
    )

fun LeaveTypeRow.toType(json: ObjectMapper): LeaveType =
    LeaveType(
        id,
        code,
        effectiveFrom,
        json.readValue(details.data(), LeavePolicyData::class.java).toPolicy(),
        active,
        version,
        revision,
    )

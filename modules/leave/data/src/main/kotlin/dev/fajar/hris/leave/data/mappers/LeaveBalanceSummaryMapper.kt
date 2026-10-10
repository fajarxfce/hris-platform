package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.leave.data.models.LeaveBalanceSummaryRow
import dev.fajar.hris.leave.domain.entities.LeaveBalanceSummary

fun LeaveBalanceSummaryRow.toSummary(): LeaveBalanceSummary =
    LeaveBalanceSummary(account.typeId, typeCode, typeName, account.toBalance())

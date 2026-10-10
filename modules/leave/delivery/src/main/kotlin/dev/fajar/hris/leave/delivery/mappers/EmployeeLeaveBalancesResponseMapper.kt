package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveEmployeeReference.toResponse(): LeaveEmployeeReferenceResponse =
    LeaveEmployeeReferenceResponse(id, number, name)

fun EmployeeLeaveBalances.toResponse(): EmployeeLeaveBalancesResponse =
    EmployeeLeaveBalancesResponse(
        employee.toResponse(),
        year,
        Page(
            balances.items.map {
                LeaveBalanceSummaryResponse(
                    it.typeId,
                    it.typeCode,
                    it.typeName,
                    it.balance.toResponse(),
                )
            },
            balances.nextCursor,
        ),
    )

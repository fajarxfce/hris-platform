package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.core.domain.Page
import java.util.UUID

data class LeaveLedger(
    val balance: LeaveBalance,
    val entries: Page<LeaveLedgerEntry>,
    val employee: LeaveEmployeeReference,
    val typeId: UUID,
    val typeCode: String,
    val typeName: String,
    val availableActions: Set<LeaveBalanceAction>,
)

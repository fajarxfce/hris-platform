package dev.fajar.hris.leave.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.util.UUID

data class LeaveLedgerResponse(
    val balance: LeaveBalanceResponse,
    val entries: Page<LeaveLedgerEntryResponse>,
    val employee: LeaveEmployeeReferenceResponse,
    val typeId: UUID,
    val typeCode: String,
    val typeName: String,
    val availableActions: Set<String>,
)

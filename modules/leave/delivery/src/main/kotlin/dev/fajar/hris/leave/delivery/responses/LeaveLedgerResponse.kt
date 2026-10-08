package dev.fajar.hris.leave.delivery.responses

import dev.fajar.hris.core.domain.Page

data class LeaveLedgerResponse(
    val balance: LeaveBalanceResponse,
    val entries: Page<LeaveLedgerEntryResponse>,
)

package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.core.domain.Page

data class LeaveLedger(val balance: LeaveBalance, val entries: Page<LeaveLedgerEntry>)

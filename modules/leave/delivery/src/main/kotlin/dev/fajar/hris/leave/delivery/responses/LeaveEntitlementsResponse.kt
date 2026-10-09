package dev.fajar.hris.leave.delivery.responses

import java.time.*

data class LeaveEntitlementsResponse(
    val balance: LeaveBalanceResponse,
    val frequency: String?,
    val postings: List<LeaveAccrualPostingResponse>,
    val closing: LeaveYearClosingResponse?,
)

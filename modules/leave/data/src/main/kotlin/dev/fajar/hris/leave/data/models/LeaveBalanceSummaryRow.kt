package dev.fajar.hris.leave.data.models

import dev.fajar.hris.schema.tables.records.LeaveAccountsRecord

data class LeaveBalanceSummaryRow(
    val account: LeaveAccountsRecord,
    val typeCode: String,
    val typeName: String,
)
